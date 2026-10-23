"""SQLite engine check of the shipped schema/query; Android integration needs a device."""
import re,sqlite3,tempfile
from pathlib import Path
source=(Path(__file__).parents[1]/'app/src/main/java/de/taxil150/app/CallHistory.java').read_text()
schema=re.search(r'db.execSQL\("([^"]+)"\)',source)[1]
query=re.search(r'db.rawQuery\("([^"]+)"',source)[1]
with tempfile.TemporaryDirectory() as tmp:
 path=Path(tmp)/'calls.db'; db=sqlite3.connect(path);db.execute(schema)
 def record(number,call_id,target,stamp):
  db.execute('INSERT OR IGNORE INTO calls(number,call_id,target,detected_at) VALUES(?,?,?,?)',(number,call_id,target,stamp))
  if target:db.execute('UPDATE calls SET target=? WHERE number=? AND call_id=? AND target<>?',(target,number,call_id,target))
 record('L150','100','',1000);record('L150','100','1N',2000);record('M150','100','1S',3000)
 assert db.execute('SELECT COUNT(*) FROM calls').fetchone()[0]==2
 assert db.execute('SELECT detected_at,target FROM calls WHERE number=?',('L150',)).fetchone()==(1000,'1N')
 db.commit();db.close();db=sqlite3.connect(path)
 assert db.execute(query,('0',)).fetchone()==('M150','100','1S',3000)
 for i in range(60):record('L150',str(200+i),'1N',4000+i)
 first=db.execute(query,('0',)).fetchall();second=db.execute(query,('50',)).fetchall()
 assert len(first)==50 and len(second)==12
 assert len({(r[0],r[1]) for r in first+second})==62
 db.close()
print('PASS: shipped SQLite schema/query, duplicate calls, per-taxi identity, original detection retained, target update, reopen persistence, full history pagination')
