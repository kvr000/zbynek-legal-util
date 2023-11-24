const exhibitMap =
{
  "20160217 exhibit desc" : "AA",
  "20190716 second exhibit" : "AB",
}
;

function processExhibits() {
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
    for (let pos = 0; match = /(["“”])((EXH(?:IBIT)?\s(?:\w+(?:\sp\d+)?|[--]|\w+(?:\sp\d+)?\s+[-]))\s([0-9][^"“”]+))(["“”])/.exec(text.substring(pos)); ) {
      const exhibitId = exhibitMap[match[4]];
      if (exhibitId == null) {
        Logger.log("Not found exhibit: " + match[4]);
        pos += match.index + match[0].length;
      }
      else {
        //Logger.log("Exhibit replacement: " + match[2] + " to: " + exhibitId);
        const replace = match[1] + "EXHIBIT " + exhibitId + " " + match[4] + match[5];
        text = text.substring(0, pos+match.index) + replace + text.substring(pos+match.index+match[0].length);
        pos += match.index+replace.length;
        used[match[4]] = true;
        replaced = true;
      }
    }
    if (replaced) {
      parent.setText(text);
    }
  }
  else if (parent.getNumChildren) {
    for (let i = 0; i < parent.getNumChildren(); ++i) {
      processExhibitsSub(parent.getChild(i), used);
    }
  }
}
