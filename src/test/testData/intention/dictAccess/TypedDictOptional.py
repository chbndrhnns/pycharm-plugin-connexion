from typing import TypedDict
class Movie(TypedDict, total=False):
    title: str
    year: int

m: Movie = {"title": "Alien", "year": 1979}
r = m["year"<caret>]