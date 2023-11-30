const exhibitMap =
{
	"20160217 exhibit desc" : { text: "AA p11", url: "https://my-storage.com/files/exhibits/20160217 exhibit desc.pdf" },
	"20190716 second exhibit" : { text: "AB p15", url: "https://my-storage.com/files/exhibits/20190716 second exhibit.pdf" }
}
;

function processExhibits()
{
	const doc = DocumentApp.getActiveDocument();
	const body = doc.getBody();
	const edit = body.editAsText();
	Logger.log(body);

	Logger.log("Searching...");
	used = {};
	processExhibitsSub(body, used);
	for (const key in exhibitMap) {
		if (!used[key]) {
			Logger.log("Unused: " + key);
		}
	}
}

function processExhibitsSub(parent, used)
{
	if (parent.getType() == DocumentApp.ElementType.TEXT) {
		let text = parent.getText();
		let match;
		let replaced = false;
		const urls = [];
		for (let pos = 0; match = /(?<=["“”])((EXH(?:IBIT)?\s(?:\w+(?:\sp\d+)?|[--]|\w+(?:\sp\d+)?\s+[-]))\s([0-9][^"“”]+))(?=["“”])/.exec(text.substring(pos)); ) {
			const exhibit = exhibitMap[match[3]];
			if (exhibit == null) {
				Logger.log("Not found exhibit: " + match[3]);
				pos += match.index + match[0].length;
			}
			else {
				const exhibitId = exhibit['text'];
				//Logger.log("Exhibit replacement: " + match[2] + " to: " + exhibitId);
				pos += match.index;
				const replace = "EXHIBIT " + exhibitId + " " + match[3];
				text = text.substring(0, pos) + replace + text.substring(pos+match[0].length);
				if (exhibit['url']) {
					urls.push([ pos, replace.length, exhibit['url']]);
				}
				pos += replace.length;
				used[match[3]] = true;
				replaced = true;
			}
		}
		if (replaced) {
			parent.setText(text);
			for (const url of urls) {
				parent.setLinkUrl(url[0], url[0] + url[1] - 1, url[2]);
			}
		}
	}
	else if (parent.getNumChildren) {
		for (let i = 0; i < parent.getNumChildren(); ++i) {
			processExhibitsSub(parent.getChild(i), used);
		}
	}
}
