from django import template

register = template.Library()


@register.inclusion_tag('tournament/bot_match.html')
def bot_match(match_result):
    return {
        'match_result': match_result,
    }

@register.inclusion_tag('tournament/match_result.html')
def match_result(match):
    return {
        'match': match,
    }

@register.inclusion_tag('tournament/result_ordered.html')
def result_ordered(match_result):
    results = match_result.match.results.order_by('rank').all()
    return {
        'results': results,
    }

@register.inclusion_tag('tournament/result_ordered.html')
def match_ordered(match):
    results = match.results.order_by('rank').all()
    return {
        'results': results,
    }
