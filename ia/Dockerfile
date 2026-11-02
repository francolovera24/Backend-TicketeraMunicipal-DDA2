# Opcional. En Windows conviene correr servidor.py en el host (ahi esta la GPU).
# Este contenedor usa torch de CPU y baja el modelo al arrancar.
FROM python:3.12-slim

WORKDIR /ia

RUN pip install --no-cache-dir --upgrade pip \
    && pip install --no-cache-dir torch torchvision --index-url https://download.pytorch.org/whl/cpu \
    && pip install --no-cache-dir transformers accelerate fastapi uvicorn pillow

COPY servidor.py .

ENV MODEL_ID=google/gemma-4-E4B-it
ENV PORT=8090
EXPOSE 8090

CMD ["python", "servidor.py"]
