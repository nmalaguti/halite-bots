# -*- coding: utf-8 -*-
# Generated by Django 1.10.5 on 2017-01-07 23:42
from __future__ import unicode_literals

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('tournament', '0001_initial'),
    ]

    operations = [
        migrations.AddField(
            model_name='matchresult',
            name='last_frame_alive',
            field=models.IntegerField(default=0),
            preserve_default=False,
        ),
    ]