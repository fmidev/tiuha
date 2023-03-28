FROM python:3.7

WORKDIR /app

RUN python -m pip install pipenv==2021.5.29

COPY Pipfile Pipfile.lock /app/

RUN PIPENV_VENV_IN_PROJECT=1 python -m pipenv install --deploy

COPY src /app/src

RUN chmod -R 775 /root/.local

ENTRYPOINT [ "python", "-m", "pipenv", "run", "python", "/app/src/main.py" ]
