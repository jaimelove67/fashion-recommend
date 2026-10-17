"""Preliminary text restrictions. Neutral captions still require visual review before publication."""
import re

BODY_OR_SUGGESTIVE = re.compile(
    r'身体特写|胸部特写|臀部特写|事业线特写|秀胸|秀臀|爆乳|福利姬|擦边|性暗示|'
    r'挑逗写真|诱惑写真|情趣内衣|露点|裸聊|nsfw|onlyfans', re.I)


def text_rejection(value):
    return 'body-focused-or-suggestive' if BODY_OR_SUGGESTIVE.search(str(value or '')) else None
