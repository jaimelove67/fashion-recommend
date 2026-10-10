FROM public.ecr.aws/docker/library/python:3.13-slim
WORKDIR /app
COPY requirements-public.txt ./
RUN pip install --no-cache-dir -r requirements-public.txt && python -m playwright install --with-deps chromium
COPY collect_public.py daily_collector.py ./
CMD ["python", "daily_collector.py", "--serve", "--host", "0.0.0.0", "--output", "/app/data"]
