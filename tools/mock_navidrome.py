from http.server import ThreadingHTTPServer,BaseHTTPRequestHandler
from urllib.parse import urlparse,parse_qs
import json,hashlib,io,wave,struct,zlib,time,threading,subprocess
from pathlib import Path
songs=[];albums=[];starred=set();events=[]
for i in range(235):
 album={'id':f'a{i:03}','name':f'Album {i:03}','artist':f'Artist {i%12:02}','albumArtist':f'Artist {i%12:02}','coverArt':f'c{i:03}','year':2000+i%25,'songCount':3}
 albums.append(album)
 for j in range(3):songs.append({'id':f's{i:03}-{j}','title':f'Track {j+1} - Album {i:03}','artist':album['artist'],'albumArtist':album['artist'],'albumId':album['id'],'album':album['name'],'coverArt':album['coverArt'],'year':album['year'],'discNumber':1,'track':j+1,'duration':120,'genre':'Jazz','recordLabels':[{'name':'Fixture Records'}],'path':f"{album['artist']}/{album['name']}/{j+1:02}.wav"})
buf=io.BytesIO()
with wave.open(buf,'wb') as w:w.setnchannels(1);w.setsampwidth(2);w.setframerate(8000);w.writeframes(b'\0\0'*8000*120)
audio=buf.getvalue()
mp3_path=Path('/tmp/navifold-fixture-cbr.mp3')
if not mp3_path.exists():subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','lavfi','-i','anullsrc=r=48000:cl=stereo','-t','120','-codec:a','libmp3lame','-b:a','192k','-write_xing','0','-id3v2_version','0','-f','mp3',str(mp3_path)],check=True)
mp3=mp3_path.read_bytes()
mp3_320_path=Path('/tmp/navifold-fixture-cbr320.mp3')
if not mp3_320_path.exists():subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','lavfi','-i','anullsrc=r=48000:cl=stereo','-t','120','-codec:a','libmp3lame','-b:a','320k','-write_xing','0','-id3v2_version','0','-f','mp3',str(mp3_320_path)],check=True)
mp3_320=mp3_320_path.read_bytes()
def png(num):
 def chunk(t,d):return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
 colors=[(48,79,120),(77,112,100),(135,88,100),(98,86,139)]
 r,g,b=colors[num%4];rows=[]
 for y in range(160):
  line=bytearray()
  for x in range(160):
   color=(225,170,86) if x<80 and y<80 else (66,121,123) if x>=80 and y<80 else (154,71,82) if x<80 else (43,48,68)
   line.extend((236,224,201) if abs(x-y)<3 or (x//12+y//12)%7==0 else color)
  rows.append(b'\0'+bytes(line))
 return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',160,160,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(b''.join(rows)))+chunk(b'IEND',b'')
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def do_GET(self):
  parsed=urlparse(self.path);q={k:v[0] for k,v in parse_qs(parsed.query,keep_blank_values=True).items()};method=parsed.path.split('/')[-1].replace('.view','')
  events.append((method,q.get('offset',q.get('songOffset','')),q.get('type','')))
  response={'status':'ok','version':'1.16.1','type':'mock','serverVersion':'fixture'}
  if q.get('u')!='demo' or q.get('t')!=hashlib.md5(('demo'+q.get('s','')).encode()).hexdigest():response.update(status='failed',error={'code':40,'message':'Wrong credentials'})
  elif method=='getCoverArt':return self.send(png(int(q['id'][1:])),'image/png')
  elif method=='stream':
   if q.get('format')=='mp3':
    # Non-seekable HTTP body: no Content-Length or Xing header, ignores Range.
    self.send_response(200);self.send_header('Content-Type','audio/mpeg');self.send_header('Accept-Ranges','none');self.end_headers()
    try:self.wfile.write(mp3_320 if q.get('maxBitRate')=='320' else mp3)
    except (BrokenPipeError,ConnectionResetError):pass
    return
   start=0;end=len(audio)-1;status=200
   if self.headers.get('Range'):
    value=self.headers['Range'].replace('bytes=','').split('-');start=int(value[0] or '0');end=int(value[1]) if value[1] else end;status=206
   self.send_response(status);self.send_header('Content-Type','audio/wav');self.send_header('Accept-Ranges','bytes');self.send_header('Content-Length',str(end-start+1))
   if status==206:self.send_header('Content-Range',f'bytes {start}-{end}/{len(audio)}')
   self.end_headers()
   try:self.wfile.write(audio[start:end+1])
   except (BrokenPipeError,ConnectionResetError):pass
   return
  elif method=='getAlbumList2':
   data=list(albums);kind=q.get('type')
   if kind=='alphabeticalByArtist':data.sort(key=lambda x:(x['artist'],x['name']))
   elif kind=='alphabeticalByName':data.sort(key=lambda x:x['name'])
   elif kind=='byYear':data.sort(key=lambda x:-x['year'])
   elif kind=='starred':data=[a for a in data if a['id'] in starred]
   else:data.reverse()
   offset=int(q.get('offset',0));size=int(q.get('size',10));time.sleep(.15);response['albumList2']={'album':data[offset:offset+size]}
  elif method=='getAlbum':response['album']={'genre':'Jazz','recordLabels':[{'name':'Fixture Records'}],'song':[s for s in songs if s['albumId']==q.get('id')]}
  elif method=='getRandomSongs':response['randomSongs']={'song':songs[:int(q.get('size',40))]}
  elif method=='getSong':
   found=next((dict(s) for s in songs if s['id']==q.get('id')),None)
   if found is None:response.update(status='failed',error={'code':70,'message':'missing'})
   else:
    found.update(genre='Jazz',suffix='wav',bitRate=128,samplingRate=8000,channelCount=1,bitDepth=16,size=len(audio),comment='Local metadata test fixture')
    response['song']=found
  elif method=='getMusicFolders':response['musicFolders']={'musicFolder':[{'id':'1','name':'Fixture Music'}]}
  elif method=='search3':
   term=q.get('query','').lower();data=[s for s in songs if term in (s['title']+' '+s['artist']).lower()];off=int(q.get('songOffset',0));count=int(q.get('songCount',20))
   adata=[a for a in albums if term in (a['name']+' '+a['artist']).lower()];aoff=int(q.get('albumOffset',0));acount=int(q.get('albumCount',20))
   response['searchResult3']={'song':data[off:off+count],'album':adata[aoff:aoff+acount]}
  elif method=='getPlaylists':response['playlists']={'playlist':[{'id':'p1','name':'Fixture Playlist','songCount':5}]}
  elif method=='getPlaylist':response['playlist']={'entry':songs[:5]}
  elif method=='star':starred.add(q['albumId'])
  elif method=='unstar':starred.discard(q['albumId'])
  elif method=='testEvents':response['events']=events
  elif method!='ping':response.update(status='failed',error={'code':70,'message':'unknown fixture endpoint'})
  self.send(json.dumps({'subsonic-response':response}).encode(),'application/json')
 def send(self,data,mime):
  self.send_response(200);self.send_header('Content-Type',mime);self.send_header('Content-Length',str(len(data)));self.end_headers()
  try:self.wfile.write(data)
  except (BrokenPipeError,ConnectionResetError):pass
print('Fixture ready: 235 albums / 705 songs',flush=True)
ThreadingHTTPServer(('127.0.0.1',18765),Handler).serve_forever()
