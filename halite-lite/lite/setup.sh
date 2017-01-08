#!/usr/bin/env bash

cd "$(dirname "$0")"

. ../venv/bin/activate

rm -f db.sqlite3

rm -rf tournament/migrations

python manage.py makemigrations tournament
python manage.py migrate

echo "from django.contrib.auth.models import User; User.objects.filter(email='admin@example.com').delete(); User.objects.create_superuser('admin', 'admin@example.com', 'admin')" | python manage.py shell
