try:
    value = data["key"]
except KeyError:
    value = default_value
