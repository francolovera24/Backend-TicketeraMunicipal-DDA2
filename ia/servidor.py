"""Servicio local de Gemma 4 E4B.

Carga google/gemma-4-E4B-it con Transformers y expone la generacion que antes
hacia Gemini: el backend le manda instrucciones y un prompt, y recibe texto.
Con imagen_url el pedido sigue el formato multimodal de la ficha del modelo
(la imagen va antes del texto). El razonamiento queda apagado por defecto:
parse_response separa el pensamiento de la respuesta y al backend solo le
llega el texto final.

Uso:
    pip install -U -r requirements.txt
    set HF_TOKEN=...          (si Hugging Face pide la licencia del modelo)
    python servidor.py
"""

from __future__ import annotations

import logging
import os
import re
import threading
from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

MODEL_ID = os.environ.get("MODEL_ID", "google/gemma-4-E4B-it")
PUERTO = int(os.environ.get("PORT", "8090"))

log = logging.getLogger("gemma")

_candado = threading.Lock()
_processor = None
_model = None
_error_carga: str | None = None
_listo = False

_PENSAMIENTO = re.compile(r"<\|channel>thought\n.*?<channel\|>", re.DOTALL)
_ESPECIAL = re.compile(r"<\|[^|>]*\|>|<channel\|>")


class Parte(BaseModel):
    tipo: str
    texto: str | None = None
    media_type: str | None = None
    datos_base64: str | None = None


class Pedido(BaseModel):
    instrucciones: str = ""
    prompt: str
    max_new_tokens: int = Field(default=1024, ge=1, le=4096)
    temperatura: float = Field(default=0.0, ge=0, le=2)
    enable_thinking: bool = False
    imagen_url: str | None = None
    partes: list[Parte] | None = None


# Tabla de embeddings por capa de Gemma 4 E4B: ~5 GiB que solo se consulta por indice.
_TABLA_POR_CAPA = "model.language_model.embed_tokens_per_layer"
_SIN_CUANTIZAR = [
    "embed_tokens", "embed_tokens_per_layer", "lm_head",
    "vision_tower", "audio_tower", "embed_vision", "embed_audio",
]


def _opciones_de_carga() -> dict:
    """El modelo en bf16 pesa ~16 GiB: no entra en una GPU de 6 GiB y desbordar a disco
    termina en 'meta tensors' o en un cierre del proceso. Con GPU chica se cargan las capas en
    4 bits (~2 GiB) y solo la tabla por capa queda en RAM. GEMMA_CUANTIZAR=0 lo desactiva."""
    import torch

    opciones = {"dtype": os.environ.get("GEMMA_DTYPE", "auto"), "device_map": "auto"}
    if not torch.cuda.is_available():
        log.warning("torch sin CUDA: el modelo corre en CPU")
        return opciones
    memoria_gib = torch.cuda.get_device_properties(0).total_memory / 2**30
    if os.environ.get("GEMMA_CUANTIZAR", "1") == "0" or memoria_gib >= 20:
        return opciones
    from transformers import BitsAndBytesConfig

    log.info("GPU de %.1f GiB: capas en 4 bits, tabla por capa en RAM", memoria_gib)
    opciones["dtype"] = torch.bfloat16
    opciones["quantization_config"] = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_compute_dtype=torch.bfloat16,
        llm_int8_skip_modules=_SIN_CUANTIZAR,
        llm_int8_enable_fp32_cpu_offload=True,
    )
    opciones["device_map"] = {"": 0, _TABLA_POR_CAPA: "cpu"}
    return opciones


def _tabla_por_capa_en_cpu(model) -> None:
    """accelerate copia a la GPU toda la tabla (5 GiB) en cada paso: no entra. Se le saca el
    hook, queda en RAM y solo viajan a la GPU los vectores de los tokens consultados."""
    import torch
    from accelerate.hooks import remove_hook_from_module

    modulo = model.get_submodule(_TABLA_POR_CAPA)
    hook = getattr(modulo, "_hf_hook", None)
    if hook is None:
        return
    mapas = [h.weights_map for h in getattr(hook, "hooks", [hook]) if getattr(h, "weights_map", None) is not None]
    if not mapas:
        return
    peso = mapas[0]["weight"]
    remove_hook_from_module(modulo, recurse=True)
    modulo.weight = torch.nn.Parameter(peso.to("cpu"), requires_grad=False)
    modulo.to("cpu")  # tambien el buffer embed_scale, que quedo en la GPU
    destino = next(model.lm_head.parameters()).device
    original = modulo.forward

    def en_cpu(input_ids, *args, **kwargs):
        return original(input_ids.to("cpu"), *args, **kwargs).to(destino)

    modulo.forward = en_cpu
    log.info("tabla por capa en RAM (%.1f GiB), consulta en CPU", peso.numel() * peso.element_size() / 2**30)


def cargar() -> None:
    """Carga el modelo una vez, con el mismo codigo de la ficha de Hugging Face."""
    global _processor, _model, _error_carga, _listo
    token = os.environ.get("HF_TOKEN", "").strip()
    if token:
        os.environ["HF_TOKEN"] = token
    else:
        os.environ.pop("HF_TOKEN", None)
    try:
        from transformers import AutoModelForMultimodalLM, AutoProcessor

        log.info("cargando modelo %s", MODEL_ID)
        processor = AutoProcessor.from_pretrained(MODEL_ID)
        opciones = _opciones_de_carga()
        model = AutoModelForMultimodalLM.from_pretrained(MODEL_ID, **opciones)
        if isinstance(opciones.get("device_map"), dict):
            _tabla_por_capa_en_cpu(model)
        model.eval()
        _processor = processor
        _model = model
        _listo = True
        log.info("modelo listo en %s", getattr(model, "device", "auto"))
    except Exception as error:
        _error_carga = str(error)
        log.exception("no se pudo cargar el modelo")


@asynccontextmanager
async def vida(_app: FastAPI):
    hilo = threading.Thread(target=cargar, name="carga-gemma", daemon=True)
    hilo.start()
    yield


app = FastAPI(title="Gemma 4 E4B", lifespan=vida)


@app.get("/salud")
def salud():
    if _listo:
        return {"estado": "listo", "modelo": MODEL_ID}
    if _error_carga:
        return JSONResponse(
            status_code=503,
            content={"estado": "error", "modelo": MODEL_ID, "error": {"message": _error_carga}},
        )
    return JSONResponse(
        status_code=503,
        content={"estado": "cargando", "modelo": MODEL_ID, "error": {"message": "El modelo todavia se esta cargando"}},
    )


@app.post("/generar")
def generar(pedido: Pedido):
    if not _listo:
        mensaje = _error_carga or "El modelo todavia se esta cargando"
        return JSONResponse(status_code=503, content={"error": {"message": mensaje}})
    try:
        texto = generar_texto(pedido)
    except Exception as error:
        log.exception("fallo la generacion")
        return JSONResponse(status_code=500, content={"error": {"message": str(error)}})
    if not texto:
        return JSONResponse(status_code=502, content={"error": {"message": "Respuesta del modelo vacia"}})
    return {"texto": texto}


def generar_texto(pedido: Pedido) -> str:
    temporales: list[str] = []
    try:
        mensajes = armar_mensajes(pedido, temporales)
        return _generar(pedido, mensajes)
    finally:
        for ruta in temporales:
            try:
                os.remove(ruta)
            except OSError:
                pass


def _generar(pedido: Pedido, mensajes: list) -> str:
    with _candado:
        inputs = _processor.apply_chat_template(
            mensajes,
            tokenize=True,
            return_dict=True,
            return_tensors="pt",
            add_generation_prompt=True,
            enable_thinking=pedido.enable_thinking,
        ).to(_model.device)
        input_len = inputs["input_ids"].shape[-1]
        opciones = {"max_new_tokens": pedido.max_new_tokens}
        if pedido.temperatura > 0:
            opciones["do_sample"] = True
            opciones["temperature"] = pedido.temperatura
        else:
            opciones["do_sample"] = False
        import torch

        with torch.inference_mode():
            outputs = _model.generate(**inputs, **opciones)
        response = _processor.decode(outputs[0][input_len:], skip_special_tokens=False)
        return texto_final(_processor, response, inputs)


def armar_mensajes(pedido: Pedido, temporales: list[str]) -> list:
    """Texto: system + user. Con partes, cada foto va despues de su leyenda.

    La imagen se guarda en un archivo y se pasa en `url`, que es la clave de la
    ficha de Gemma. El processor abre tanto http como una ruta local.
    """
    mensajes = []
    if pedido.instrucciones.strip():
        mensajes.append({"role": "system", "content": pedido.instrucciones})
    if pedido.partes:
        mensajes.append({"role": "user", "content": contenido_de(pedido.partes, temporales)})
    elif pedido.imagen_url:
        mensajes.append({
            "role": "user",
            "content": [
                {"type": "image", "url": pedido.imagen_url},
                {"type": "text", "text": pedido.prompt},
            ],
        })
    else:
        mensajes.append({"role": "user", "content": pedido.prompt})
    return mensajes


def contenido_de(partes: list[Parte], temporales: list[str]) -> list:
    import base64
    import io
    import tempfile

    from PIL import Image

    bloques = []
    for parte in partes:
        if parte.tipo == "texto":
            bloques.append({"type": "text", "text": parte.texto or ""})
        elif parte.tipo == "imagen":
            crudo = base64.b64decode(parte.datos_base64 or "")
            imagen = Image.open(io.BytesIO(crudo)).convert("RGB")
            archivo = tempfile.NamedTemporaryFile(suffix=".jpg", delete=False)
            imagen.save(archivo, format="JPEG")
            archivo.close()
            temporales.append(archivo.name)
            bloques.append({"type": "image", "url": archivo.name})
        else:
            raise ValueError(f"Parte desconocida: {parte.tipo}")
    return bloques


def texto_final(processor, response: str, inputs) -> str:
    """Usa parse_response y se queda con la respuesta, sin el pensamiento."""
    parsed = None
    try:
        parsed = processor.parse_response(response, prefix=inputs["input_ids"])
    except TypeError:
        parsed = processor.parse_response(response)
    except Exception:
        log.warning("parse_response fallo; se limpia el texto crudo", exc_info=True)
    texto = contenido(parsed)
    if texto:
        return texto
    return limpiar_crudo(response)


def contenido(parsed) -> str:
    if parsed is None:
        return ""
    if isinstance(parsed, str):
        return limpiar_crudo(parsed)
    if isinstance(parsed, dict):
        for clave in ("content", "answer", "text"):
            if clave in parsed:
                plano = a_texto(parsed[clave])
                if plano:
                    return plano
        return ""
    for atributo in ("content", "answer", "text"):
        if hasattr(parsed, atributo):
            plano = a_texto(getattr(parsed, atributo))
            if plano:
                return plano
    return ""


def a_texto(valor) -> str:
    if valor is None:
        return ""
    if isinstance(valor, str):
        return limpiar_crudo(valor)
    if isinstance(valor, list):
        partes = []
        for item in valor:
            if isinstance(item, dict):
                partes.append(a_texto(item.get("text", "")))
            else:
                partes.append(a_texto(item))
        return limpiar_crudo("".join(partes))
    if isinstance(valor, dict):
        return a_texto(valor.get("text", ""))
    return limpiar_crudo(str(valor))


def limpiar_crudo(texto: str) -> str:
    sin_pensamiento = _PENSAMIENTO.sub("", texto or "")
    return _ESPECIAL.sub("", sin_pensamiento).strip()


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    uvicorn.run(app, host="0.0.0.0", port=PUERTO)
