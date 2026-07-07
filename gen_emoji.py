import urllib.request
import os.path

EMOJIS_PATH = 'res/raw/emojis.txt'
EMOJI_TEST_PATH = 'emoji-test.txt'
EMOJI_TEST_URL = 'https://unicode.org/Public/emoji/latest/emoji-test.txt'

def rawEmojiFromCodes(codes):
    return ''.join([chr(int(c, 16)) for c in codes])

def searchTextFromLine(line, group, subgroup):
    parts = line.split('#', 1)
    name = ''
    if len(parts) == 2:
        comment = parts[1].strip().split()
        if len(comment) >= 3:
            name = ' '.join(comment[2:])
    return ' '.join([name, group, subgroup]).lower()

def getEmojiTestContents():
    if os.path.exists(EMOJI_TEST_PATH):
        print(f'Using existing {EMOJI_TEST_PATH}')
    else:
        print(f'Downloading {EMOJI_TEST_URL}')
        urllib.request.urlretrieve(EMOJI_TEST_URL, EMOJI_TEST_PATH)
    return open(EMOJI_TEST_PATH, mode='r', encoding='UTF-8').read()
        

emoji_list = []
group_indices = []
group = ''
subgroup = ''
for line in getEmojiTestContents().splitlines():
    if line.startswith('# group:'):
        group = line.split(':', 1)[1].strip()
        if len(group_indices) == 0 or len(emoji_list) > group_indices[-1]:
            group_indices.append(len(emoji_list))
    elif line.startswith('# subgroup:'):
        subgroup = line.split(':', 1)[1].strip()
    elif not line.startswith('#') and 'fully-qualified' in line:
        codes = line.split(';')[0].split()
        emoji_list.append((rawEmojiFromCodes(codes),
            searchTextFromLine(line, group, subgroup)))
with open(EMOJIS_PATH, 'w', encoding='UTF-8') as emojis:
    for e, search_text in emoji_list:
        emojis.write(f'{e}\t{search_text}\n')
    emojis.write('\n')

    emojis.write(' '.join([str(g) for g in group_indices]))
    emojis.write('\n')

print(f'Parsed {len(emoji_list)} emojis in {len(group_indices)}')
