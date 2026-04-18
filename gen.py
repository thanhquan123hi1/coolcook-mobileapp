import re, base64

with open('app/src/main/res/mipmap-anydpi/Logo.svg') as f:
    content = f.read()

match = re.search(r'base64,([^"\'\\]+)', content)
png_data = base64.b64decode(match.group(1))

# Write to standard dense folder
with open('app/src/main/res/mipmap-xxxhdpi/ic_launcher.png', 'wb') as f:
    f.write(png_data)
with open('app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png', 'wb') as f:
    f.write(png_data)

print("Done")
