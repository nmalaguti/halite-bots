from django.shortcuts import render
from django.views import generic
from django.core.paginator import Paginator, EmptyPage, PageNotAnInteger

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


def bot_view(request, bot_id):
    bot = Bot.objects.get(id=bot_id)
    match_list = bot.matches.order_by('-match__date').all()
    paginator = Paginator(match_list, 10)

    page = request.GET.get('page')
    try:
        matches = paginator.page(page)
    except PageNotAnInteger:
        # If page is not an integer, deliver first page.
        matches = paginator.page(1)
    except EmptyPage:
        # If page is out of range (e.g. 9999), deliver last page of results.
        matches = paginator.page(paginator.num_pages)

    return render(request, 'tournament/bot.html', {
        'bot': bot,
        'matches': matches,
    })


class BotView(generic.DetailView):
    model = Bot
    template_name = 'tournament/bot.html'


class MatchView(generic.DetailView):
    model = Match
    template_name = 'tournament/match.html'
