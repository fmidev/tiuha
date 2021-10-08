FROM python:3.7

WORKDIR /app

RUN python -m pip install pipenv==2021.5.29
COPY Pipfile Pipfile.lock /app
RUN python -m pipenv install
COPY src /app/src

ENTRYPOINT [ "python", "-m", "pipenv", "run", "python", "/app/src/main.py" ]
