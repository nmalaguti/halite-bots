from collections import Counter

from django.conf import settings
from django.shortcuts import render
from django.views import generic
from django.core.paginator import Paginator, EmptyPage, PageNotAnInteger
from django.db.models import F

from .models import Bot, Match


class IndexView(generic.ListView):
    template_name = 'tournament/index.html'

    def get_queryset(self):
        return Bot.objects.order_by(F('mu') - (F('sigma') * 3)).reverse()


class MatchesView(generic.ListView):
    template_name = 'tournament/matches.html'
    paginate_by = settings.PAGE_SIZE

    def get_queryset(self):
        return Match.objects.order_by('-date')


def bot_view(request, bot_id):
    bot = Bot.objects.get(id=bot_id)
    paginator = Paginator(bot.matches.order_by('-match__date').all(), settings.PAGE_SIZE)

    page = request.GET.get('page')
    try:
        match_list = paginator.page(page)
    except PageNotAnInteger:
        # If page is not an integer, deliver first page.
        match_list = paginator.page(1)
    except EmptyPage:
        # If page is out of range (e.g. 9999), deliver last page of results.
        match_list = paginator.page(paginator.num_pages)

    return render(request, 'tournament/bot.html', {
        'bot': bot,
        'match_list': match_list,
    })


class MatchView(generic.DetailView):
    model = Match
    template_name = 'tournament/match.html'


def whole_history(request):
    matches = Match.objects.all().prefetch_related('results__bot')

    try:
        num_players = int(request.GET.get('num_players'))
    except (ValueError, TypeError):
        num_players = None

    try:
        min_size = int(request.GET.get('min_size', 0))
    except (ValueError, TypeError):
        min_size = 0

    try:
        max_size = int(request.GET.get('max_size', 2500))
    except (ValueError, TypeError):
        max_size = 2500

    rankings = []
    num_matches = Counter()
    for match in matches:
        ranking = {}
        results = match.results.all()
        if ((num_players is None or results.count() == num_players) and
                min_size < match.width * match.height <= max_size):
            num_matches.update([result.bot.name for result in results])
            for result in results:
                ranking[result.bot.name] = result.rank
        rankings.append(ranking)

    gammas = plackett_luce(rankings)

    normalizing_constant = sum(value for player, value in gammas.items())
    gammas = {player: value / normalizing_constant for player, value in gammas.items()}
    gammas = [(player, gamma, num_matches[player]) for gamma, player in
              sorted([(v, k) for k, v in gammas.items()], reverse=True)]

    return render(request, 'tournament/whole_history.html', {
        'gammas': gammas,
        'num_players': num_players,
        'min_size': min_size,
        'max_size': max_size,
    })


def plackett_luce(rankings):
    ''' Returns dictionary containing player : plackett_luce_parameter keys
    and values. This algorithm requires that every player avoids coming in
    last place at least once and that every player fails to win at least once.
    If this assumption fails (not checked), the algorithm will diverge.

    Input is a list of dictionaries, where each dictionary corresponds to an
    individual ranking and contains the player : finish for that ranking.

    The plackett_luce parameters returned are un-normalized and can be
    normalized by the calling function if desired.'''
    players = set(key for ranking in rankings for key in ranking.keys())
    ws = Counter(name for ranking in rankings for name, finish in ranking.items() if finish < len(ranking))
    gammas = {player: 1.0 / len(players) for player in players}
    _gammas = {player: 0 for player in players}
    while sum((gamma - _gammas[player]) ** 2 for player, gamma in gammas.items()) > 1e-9:
        denoms = {player: sum(sum(0 if player not in ranking or ranking[player] < place else 1 / sum(
            gammas[finisher] for finisher in ranking if ranking[finisher] >= place) for place in range(1, len(ranking)))
                              for ranking in rankings) for player in players}
        _gammas = gammas
        gammas = {player: ws[player] / denoms[player] for player in players}
    return gammas
