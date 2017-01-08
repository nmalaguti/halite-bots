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

@register.inclusion_tag('tournament/pagination.html')
def paginate(paginated):
    return {
        'paginated': paginated,
    }

@register.inclusion_tag('tournament/page_numbers.html')
def nearby_pages(paginated):
    neighbors = 5
    if paginated.number < (neighbors + 1):
        min_page = 1
        max_page = min((neighbors * 2 + 1), paginated.paginator.num_pages)
    elif paginated.number + (neighbors + 1) > paginated.paginator.num_pages:
        min_page = paginated.paginator.num_pages - (neighbors * 2)
        max_page = paginated.paginator.num_pages
    else:
        min_page = paginated.number - neighbors
        max_page = paginated.number + neighbors

    return {
        'page_numbers': range(max(1, min_page), min(max_page, paginated.paginator.num_pages) + 1),
        'paginated': paginated,
    }
