from django.shortcuts import render
from django.views import generic

from .models import Bot, Match


class IndexView(generic.ListView):
    template_name = 'tournament/index.html'

    def get_queryset(self):
        return Bot.objects.order_by('name')


class MatchesView(generic.ListView):
    template_name = 'tournament/matches.html'
    paginate_by = 10

    def get_queryset(self):
        return Match.objects.order_by('-date')


class BotView(generic.DetailView):
    model = Bot
    template_name = 'tournament/bot.html'


class MatchView(generic.DetailView):
    model = Match
    template_name = 'tournament/match.html'
