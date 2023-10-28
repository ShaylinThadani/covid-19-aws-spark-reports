# Generate index.html for the exported reports

import os

links = []
for root, dirs, files in os.walk("out"):
    for filename in files:
        href_link = '<p><a href="{}">{}</a></p>'.format(filename, filename.replace('.html', ''))
        links.append(href_link)


doc = """
<!DOCTYPE html>
<html>
<body>

<h2>Reports</h2>
"""

for link in links:
	doc += link


doc += """
</body>
</html>
"""

file = open("out/index.html","w") 
file.write(doc)
file.close()

        