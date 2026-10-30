/*!
 * ECM 문서 마스킹 (ecmMask.js) — 전역에는 EcmMask 하나만 노출
 *
 * 사용: var h = EcmMask.init(document.getElementById('ecmMask'), { ...설정... });
 *       ajax 로 화면을 갈아끼우는 구조면 화면 전환 전에 h.destroy() 호출 (document/window 이벤트 해제)
 *
 * 설정(cfg)
 *   api.list  GET  ?procDate&procSeq&bizCd&fileSeq&delYn&maskYn
 *             → {rows:[{procDate,procTime,procSeq,bizCd,fileSeq,fileName,entityId,delYn,maskYn,maskDt}]}
 *   api.file  GET(또는 fileMethod:'POST')  ?procDate&procSeq&bizCd&fileSeq
 *             → 파일 바이너리(image/*, application/pdf) 또는 JSON {result:'OK',contentType,base64}
 *   api.save  POST multipart: procDate,procSeq,bizCd,fileSeq,fileName,regions(JSON),page_0.png,page_1.png...
 *             → {result:'OK', maskDt:'yyyy/MM/dd HH:mm:ss'}  (그 외 result 는 실패, message 표시)
 *   ※ api 값이 빈 문자열이면 해당 호출은 MOCK(가상 데이터)으로 동작
 *   csrf             {header:'X-CSRF-TOKEN', token:'...'}   (불필요하면 생략)
 *   fileMethod       'GET'(기본) | 'POST'
 *   pdfWorker        pdf.worker.min.js 경로 (PDF.js 를 로드한 경우 필수)
 *   saveConfirm      'modal'(기본: 미리보기 모달+체크박스) | 'confirm'(confirm 창 한 번 후 바로 전송)
 *   defaultProcDate  처리일자 초기값 'YYYYMMDD' (기본 오늘)
 */
(function(global){
'use strict';

function create(root, opt){
var cfg=Object.assign({fileMethod:'GET',pdfWorker:'',saveConfirm:'modal',defaultProcDate:todayStr()},opt||{});
var api=Object.assign({list:'',file:'',save:''},cfg.api), csrf=Object.assign({header:'',token:''},cfg.csrf);

/* ---------- 유틸 ---------- */
function $(ref){ return root.querySelector('[data-ref="'+ref+'"]'); }
function esc(s){ return String(s == null ? '' : s).replace(/[&<>"']/g, function(c){
  return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]; }); }
function qs(o){ var a=[]; for (var k in o) if (o[k] !== '' && o[k] != null) a.push(encodeURIComponent(k)+'='+encodeURIComponent(o[k])); return a.join('&'); }
function toast(t){ var e=$('toast'); e.textContent=t; e.style.display='block'; clearTimeout(toast.t); toast.t=setTimeout(function(){e.style.display='none';},2500); }
function setMsg(t, cls){ var e=$('msg'); e.textContent=t; e.className='emk-info'+(cls?' emk-'+cls:''); }
function fmtDate(d){ return d && d.length===8 ? d.substr(0,4)+'/'+d.substr(4,2)+'/'+d.substr(6,2) : (d||''); }
function fmtTime(t){ return t && t.length===6 ? t.substr(0,2)+':'+t.substr(2,2)+':'+t.substr(4,2) : (t||''); }
function rowKey(r){ return r.procDate+'|'+r.procSeq+'|'+r.fileSeq; }

/* ---------- MOCK ---------- */
var MOCK_ROWS = (function(){
  var names=['ECM_C780411E_00001_1.jpg','ECM_C780411E_00001_2.jpg','ECM_5A1F8D65_00001_1.jpg','ECM_5A1F8D65_00001_2.jpg'], rows=[];
  for (var i=0;i<24;i++){
    var seq = 15 - Math.floor(i/2);
    rows.push({procDate:cfg.defaultProcDate,procTime:i<2?'133233':'130323',procSeq:seq,bizCd:'SR02',fileSeq:i%2+1,
      fileName: i===5 ? 'ECM_SAMPLE_00003_PDF.pdf' : (i<4?names[i]:'ECM_'+(1000+i)+'_00001_'+(i%2+1)+'.jpg'),
      entityId:'2011869183',delYn:'N',maskYn:i===3?'Y':'N',maskDt:i===3?'2026/10/22 14:00:00':''});
  }
  return rows;
})();
function mockPdf(){
  var parts=['%PDF-1.4\n'], offs=[], pos=9;
  function add(t){ offs.push(pos); parts.push(t); pos+=t.length; }
  function page(n){ var c='BT /F1 28 Tf 60 760 Td (Sample PDF page '+(n-1)/2+') Tj /F1 18 Tf 0 -60 Td (Name: Hong Gil Dong) Tj 0 -40 Td (RRN: 800101-1234567) Tj ET';
    add(n+' 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 595 842]/Contents '+(n+1)+' 0 R/Resources<</Font<</F1 7 0 R>>>>>>endobj\n');
    add((n+1)+' 0 obj<</Length '+c.length+'>>stream\n'+c+'\nendstream endobj\n'); }
  add('1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n'); add('2 0 obj<</Type/Pages/Kids[3 0 R 5 0 R]/Count 2>>endobj\n');
  page(3); page(5); add('7 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj\n');
  var x='xref\n0 8\n0000000000 65535 f \n';
  offs.forEach(function(o){ x+=('0000000000'+o).slice(-10)+' 00000 n \n'; });
  parts.push(x+'trailer<</Root 1 0 R/Size 8>>\nstartxref\n'+pos+'\n%%EOF');
  return new Blob(parts,{type:'application/pdf'});
}
function mockSearch(p){
  return MOCK_ROWS.filter(function(r){
    return (!p.procDate||r.procDate===p.procDate)&&(!p.procSeq||String(r.procSeq)===p.procSeq)&&(!p.bizCd||r.bizCd===p.bizCd)
      &&(!p.fileSeq||String(r.fileSeq)===p.fileSeq)&&(!p.delYn||r.delYn===p.delYn)&&(!p.maskYn||r.maskYn===p.maskYn);
  });
}
function mockFile(row){
  return new Promise(function(res){
    var c=document.createElement('canvas'); c.width=900; c.height=1200; var g=c.getContext('2d');
    g.fillStyle='#fff'; g.fillRect(0,0,900,1200); g.fillStyle='#222'; g.font='bold 34px sans-serif';
    g.fillText('종합소득세 신고서 (샘플)',230,80); g.font='22px sans-serif';
    var lines=['성명: 홍길동','주민등록번호: 800101-1234567','주소: 서울특별시 서초구 서초대로 000','소득금액: 12,345,678 원','',row.fileName];
    lines.forEach(function(t,i){ g.fillText(t,80,200+i*60); });
    g.strokeRect(60,150,780,420);
    c.toBlob(res,'image/png');
  });
}

/* ---------- API 래퍼 ---------- */
function fetchList(p){
  if (!api.list) return Promise.resolve(mockSearch(p));
  return fetch(api.list+'?'+qs(p),{credentials:'same-origin'}).then(chk).then(function(r){return r.json();}).then(function(j){
    if (j.result && j.result!=='OK') throw new Error(j.message||'조회 실패');
    return j.rows||[];
  });
}
/* ★ 문서 읽어오기(행 클릭) 시 호출되는 ECM 조회 API — api.file 에 주소만 넣으면 동작
 *   요청: GET api.file?procDate&procSeq&fileSeq   (POST 가 필요하면 cfg.fileMethod='POST' → 같은 값이 form 으로 전송)
 *   응답: ① 파일 바이너리(Content-Type: image/*, application/pdf)
 *         ② JSON {result:'OK', contentType:'image/jpeg', base64:'...'}  (실패 시 result!='OK', message)  */
function fetchFile(row){
  if (!api.file) return row.fileName.match(/\.pdf$/i) ? Promise.resolve(mockPdf()) : mockFile(row);
  var p={procDate:row.procDate,procSeq:row.procSeq,bizCd:row.bizCd,fileSeq:row.fileSeq}, h={}, opt={credentials:'same-origin',headers:h}, url=api.file;
  if (csrf.header) h[csrf.header]=csrf.token;
  if (cfg.fileMethod==='POST'){ opt.method='POST'; h['Content-Type']='application/x-www-form-urlencoded; charset=UTF-8'; opt.body=qs(p); }
  else url+='?'+qs(p);
  return fetch(url,opt).then(chk).then(function(r){
    if (/json/i.test(r.headers.get('Content-Type')||'')) return r.json().then(function(j){
      if (j.result!=='OK') throw new Error(j.message||'문서 조회 실패');
      var bin=atob(j.base64), u8=new Uint8Array(bin.length); for (var i=0;i<bin.length;i++) u8[i]=bin.charCodeAt(i);
      return new Blob([u8],{type:j.contentType||''});
    });
    return r.blob();
  });
}
function postSave(fd){
  if (!api.save) return new Promise(function(res){ setTimeout(function(){ res({result:'OK',maskDt:nowStr()}); },700); });
  var h={}; if (csrf.header) h[csrf.header]=csrf.token;
  return fetch(api.save,{method:'POST',body:fd,headers:h,credentials:'same-origin'}).then(chk).then(function(r){return r.json();});
}
function chk(r){ if(!r.ok) throw new Error('HTTP '+r.status); return r; }
function nowStr(){ var d=new Date(),p=function(n){return n<10?'0'+n:n;};
  return d.getFullYear()+'/'+p(d.getMonth()+1)+'/'+p(d.getDate())+' '+p(d.getHours())+':'+p(d.getMinutes())+':'+p(d.getSeconds()); }

/* ============================================================
 * 좌측 리스트
 * ponytail: 조회 결과 전체를 받아 클라이언트 페이징. 건수가 수천 이상이면 서버 페이징으로 전환.
 * ============================================================ */
var S = { rows:[], page:1, size:10, cur:null };

function filters(){ return {procDate:$('fProcDate').value.trim(),procSeq:$('fProcSeq').value.trim(),bizCd:$('fBizCd').value,
  fileSeq:$('fFileSeq').value.trim(),delYn:$('fDelYn').value,maskYn:$('fMaskYn').value}; }

function search(){
  $('tbody').innerHTML='<tr><td colspan="9" class="emk-mut">조회 중...</td></tr>';
  fetchList(filters()).then(function(rows){ S.rows=rows; S.page=1; renderList(); })
    .catch(function(e){ $('tbody').innerHTML='<tr><td colspan="9" style="color:var(--red)">조회 실패: '+esc(e.message)+'</td></tr>'; });
}

function renderList(){
  var rows=S.rows, total=rows.length, done=rows.filter(function(r){return r.maskYn==='Y';}).length;
  var pct=total?Math.round(done*100/total):0;
  $('sum').innerHTML='<span class="emk-chip">조회 <b>'+total+'</b>건</span><span class="emk-chip emk-ok">마스킹완료 <b>'+done+'</b></span>'
    +'<span class="emk-chip emk-warn">미처리 <b>'+(total-done)+'</b></span><div class="emk-bar"><i style="width:'+pct+'%"></i></div><span class="emk-mut">'+pct+'%</span>';
  var cnt={}; rows.forEach(function(r){ cnt[r.procDate+r.procSeq]=(cnt[r.procDate+r.procSeq]||0)+1; });
  var st=(S.page-1)*S.size, part=rows.slice(st,st+S.size), h='';
  part.forEach(function(r,i){
    var b = r.delYn==='Y' ? '<span class="badge badge-secondary bg-secondary">삭제됨</span>'
          : r.maskYn==='Y' ? '<span class="badge badge-success bg-success" title="'+esc(r.maskDt)+'">마스킹완료</span>' : '<span class="badge badge-warning bg-warning text-dark">미처리</span>';
    h+='<tr data-i="'+(st+i)+'" class="'+(S.cur&&rowKey(S.cur)===rowKey(r)?'emk-sel':'')+'"><td>'+b+'</td><td>'+fmtDate(r.procDate)+'</td><td>'+fmtTime(r.procTime)
      +'</td><td>'+esc(r.procSeq)+' <span class="emk-mut">('+cnt[r.procDate+r.procSeq]+')</span></td><td>'+esc(r.bizCd)+'</td><td>'+esc(r.fileSeq)
      +'</td><td class="emk-l">'+esc(r.fileName)+'</td><td>'+esc(r.entityId)+'</td><td>'+esc(r.delYn)+'</td></tr>';
  });
  $('tbody').innerHTML = h || '<tr><td colspan="9" class="emk-mut">조회된 데이터가 없습니다.</td></tr>';
  $('pgInfo').textContent = total ? 'Showing '+(st+1)+' to '+(st+part.length)+' of '+total+' entries' : 'Showing 0 entries';
  var pages=Math.max(1,Math.ceil(total/S.size)), ph='<button class="btn btn-sm btn-light" data-p="'+Math.max(1,S.page-1)+'">&lt;</button>';
  var a=Math.max(1,S.page-2), z=Math.min(pages,a+4); a=Math.max(1,z-4);
  for (var p=a;p<=z;p++) ph+='<button data-p="'+p+'" class="btn btn-sm '+(p===S.page?'btn-primary emk-on':'btn-light')+'">'+p+'</button>';
  $('pg').innerHTML=ph+'<button class="btn btn-sm btn-light" data-p="'+Math.min(pages,S.page+1)+'">&gt;</button>';
}

$('pg').onclick=function(e){ var p=e.target.dataset.p; if(p){ S.page=+p; renderList(); } };
$('tbody').onclick=function(e){ var tr=e.target.closest('tr[data-i]'); if(tr) openRow(S.rows[+tr.dataset.i]); };
$('pageSize').onchange=function(){ S.size=+this.value; S.page=1; renderList(); };
$('btnSearch').onclick=search;
$('btnInit').onclick=function(){ ['fProcDate','fProcSeq','fFileSeq'].forEach(function(i){$(i).value='';});
  $('fBizCd').value='';$('fDelYn').value='N';$('fMaskYn').value=''; search(); };
root.querySelectorAll('.emk-filters input').forEach(function(i){ i.onkeydown=function(e){ if(e.key==='Enter') search(); }; });

/* ============================================================
 * 우측 뷰어 / 마스킹
 * D = {pages:[{src:Canvas, regions:[{x,y,w,h,type}]}], idx, kind:'img'|'pdf'|'pdfview'}
 * 좌표는 모두 원본 픽셀 기준 (표시 배율과 무관)
 * ============================================================ */
var D=null, pending=null, sel=-1 /*선택된 마스크 index(현재 페이지)*/, zoom=0 /*0=맞춤*/, drag=null, busy=false;
var cv=$('cv'), ctx=cv.getContext('2d');

function dirty(){ return !!D && D.pages.some(function(p){return p.regions.length;}); }
function totalRegions(){ return D ? D.pages.reduce(function(n,p){return n+p.regions.length;},0) : 0; }

function openRow(row){
  if (busy) return;
  if (dirty() && !confirm('저장하지 않은 마스킹 내용이 있습니다. 다른 문서를 열면 사라집니다. 계속하시겠습니까?')) return;
  S.cur=row; renderList(); closeDoc();
  $('curName').textContent='- '+row.fileName; setMsg('문서를 불러오는 중...');
  if (row.delYn==='Y'){ setMsg('삭제된 문서는 열 수 없습니다.','err'); return; }
  fetchFile(row).then(loadBlob.bind(null,row)).catch(function(e){ setMsg('문서 로드 실패: '+e.message,'err'); });
}

function closeDoc(){
  D=null; pending=null; sel=-1; cv.hidden=true; $('empty').style.display='flex'; $('pdfFrame').style.display='none'; $('pdfFrame').removeAttribute('src');
  ui();
}

function loadBlob(row, blob){
  var isPdf = blob.type==='application/pdf' || /\.pdf$/i.test(row.fileName);
  if (isPdf) return loadPdf(row, blob);
  return createImageBitmap(blob).then(function(bm){
    var c=document.createElement('canvas'); c.width=bm.width; c.height=bm.height; c.getContext('2d').drawImage(bm,0,0);
    D={pages:[{src:c,regions:[]}],idx:0,kind:'img'}; show();
  }).catch(function(){ setMsg('지원하지 않는 이미지 형식입니다. ('+(blob.type||'알 수 없음')+')','err'); });
}

function loadPdf(row, blob){
  if (window.pdfjsLib){
    if (cfg.pdfWorker) pdfjsLib.GlobalWorkerOptions.workerSrc=cfg.pdfWorker;
    return blob.arrayBuffer().then(function(buf){ return pdfjsLib.getDocument({data:buf}).promise; }).then(function(pdf){
      var pages=[], chain=Promise.resolve();
      for (var n=1;n<=pdf.numPages;n++) (function(n){ chain=chain.then(function(){ return pdf.getPage(n).then(function(pg){
        var vp=pg.getViewport({scale:1.5}), c=document.createElement('canvas'); c.width=vp.width; c.height=vp.height;
        return pg.render({canvasContext:c.getContext('2d'),viewport:vp}).promise.then(function(){ pages.push({src:c,regions:[]}); });
      }); }); })(n);
      return chain.then(function(){ D={pages:pages,idx:0,kind:'pdf'}; show(); });
    }).catch(function(e){ setMsg('PDF 렌더링 실패: '+e.message,'err'); });
  }
  /* PDF.js 미반입: 브라우저 내장 뷰어로 보기만 가능 (마스킹 불가)
   * ponytail: 반입 전 대안 = 서버에서 PDF→이미지 변환 API 를 file 주소로 제공(image/png 반환) */
  D={pages:[],idx:0,kind:'pdfview'}; $('empty').style.display='none';
  $('pdfFrame').style.display='block'; $('pdfFrame').src=URL.createObjectURL(blob);
  setMsg('PDF 마스킹은 PDF.js 라이브러리 반입 후 사용 가능합니다. (현재 보기만 가능)','err'); ui();
}

function show(){
  zoom=0; $('empty').style.display='none'; cv.hidden=false; draw(); ui();
  setMsg(S.cur.maskYn==='Y' ? '이미 마스킹 완료된 문서입니다. 필요 시 다시 마스킹할 수 있습니다.' : '영역을 드래그한 뒤 M(또는 Enter)을 누르면 마스킹됩니다.');
}

/* 한 페이지를 그림. overlay=true 이면 선택중인 영역 표시(편집화면 전용) */
function paint(g, pg, overlay){
  var s=pg.src; g.drawImage(s,0,0);
  pg.regions.forEach(function(r){
    if (r.type==='mosaic'){
      var b=Math.max(10,Math.round(Math.min(r.w,r.h)/4)), tw=Math.max(1,Math.ceil(r.w/b)), th=Math.max(1,Math.ceil(r.h/b));
      var t=document.createElement('canvas'); t.width=tw; t.height=th;
      t.getContext('2d').drawImage(s,r.x,r.y,r.w,r.h,0,0,tw,th);
      g.imageSmoothingEnabled=false; g.drawImage(t,0,0,tw,th,r.x,r.y,r.w,r.h); g.imageSmoothingEnabled=true;
    } else { g.fillStyle=r.type==='white'?'#fff':'#000'; g.fillRect(r.x,r.y,r.w,r.h); }
  });
  if (overlay && pending){
    g.save(); g.lineWidth=Math.max(2,s.width/400); g.strokeStyle='#e03131'; g.setLineDash([8,6]); g.fillStyle='rgba(224,49,49,.15)';
    g.fillRect(pending.x,pending.y,pending.w,pending.h); g.strokeRect(pending.x,pending.y,pending.w,pending.h); g.restore();
  }
  if (overlay && sel>=0 && pg.regions[sel]){   // 선택된 마스크: 파란 테두리 + 모서리 핸들
    var r=pg.regions[sel], lw=Math.max(2,s.width/400), hs=handle(), cs=corners(r);
    g.save(); g.lineWidth=lw; g.strokeStyle='#1c7ed6'; g.strokeRect(r.x,r.y,r.w,r.h); g.fillStyle='#fff';
    for (var c in cs){ g.fillRect(cs[c][0]-hs/2,cs[c][1]-hs/2,hs,hs); g.strokeRect(cs[c][0]-hs/2,cs[c][1]-hs/2,hs,hs); }
    g.restore();
  }
}

function draw(){
  if (!D || !D.pages.length) return;
  var pg=D.pages[D.idx]; cv.width=pg.src.width; cv.height=pg.src.height;
  cv.style.width = zoom ? Math.round(pg.src.width*zoom)+'px' : '100%';
  paint(ctx,pg,true);
}

function ui(){
  var ed = !!D && D.kind!=='pdfview' && D.pages.length>0, n=ed?D.pages[D.idx].regions.length:0, tot=totalRegions();
  $('btnApply').disabled=!pending; $('btnDel').disabled=!(ed&&sel>=0); $('btnUndo').disabled=!n; $('btnReset').disabled=!tot; $('btnSave').disabled=!tot||busy; $('btnPng').disabled=!ed||!D.pages[D.idx].regions.length;
  $('btnPrev').disabled=!ed||D.idx===0; $('btnNext').disabled=!ed||D.idx>=D.pages.length-1;
  ['btnZoomIn','btnZoomOut','btnFit'].forEach(function(i){$(i).disabled=!ed;});
  $('pageNo').textContent = ed ? (D.idx+1)+' / '+D.pages.length : '0 / 0';
  $('zoomLbl').textContent = zoom ? Math.round(zoom*100)+'%' : '맞춤';
  if (ed) setMsg(tot ? '현재 '+tot+'개 영역이 마스킹되었습니다. 다른 영역을 계속 선택할 수 있습니다.' : '영역을 드래그한 뒤 M(또는 Enter)을 누르면 마스킹됩니다.');
}

/* ---------- 마우스/터치/펜 ----------
 * 빈 곳 드래그 = 새 영역 선택 · 마스크 클릭 = 선택 · 선택 후 안쪽 드래그 = 이동 · 모서리 핸들 드래그 = 크기 조절 */
function regs(){ return D.pages[D.idx].regions; }
function clamp(v,a,b){ return Math.max(a,Math.min(b,v)); }
function pt(e){ var r=cv.getBoundingClientRect(), x=(e.clientX-r.left)*cv.width/r.width, y=(e.clientY-r.top)*cv.height/r.height;
  return {x:clamp(x,0,cv.width),y:clamp(y,0,cv.height)}; }
function handle(){ var w=cv.getBoundingClientRect().width; return 10*cv.width/(w||cv.width); }   // 화면상 10px 핸들
function corners(r){ return {nw:[r.x,r.y],ne:[r.x+r.w,r.y],sw:[r.x,r.y+r.h],se:[r.x+r.w,r.y+r.h]}; }
function hit(p){
  var rs=regs(), hs=handle();
  if (sel>=0 && rs[sel]){ var cs=corners(rs[sel]); for (var c in cs) if (Math.abs(p.x-cs[c][0])<=hs && Math.abs(p.y-cs[c][1])<=hs) return {i:sel,corner:c}; }
  for (var i=rs.length-1;i>=0;i--){ var r=rs[i]; if (p.x>=r.x&&p.x<=r.x+r.w&&p.y>=r.y&&p.y<=r.y+r.h) return {i:i}; }   // 위에 그려진 것 우선
  return null;
}
var CURSOR={nw:'nwse-resize',se:'nwse-resize',ne:'nesw-resize',sw:'nesw-resize'};
cv.onpointerdown=function(e){
  if(!D||!D.pages.length||busy) return;
  var p=pt(e), h=hit(p); pending=null; cv.setPointerCapture(e.pointerId);
  if (h){ var r=regs()[h.i]; sel=h.i; $('maskType').value=r.type;
    drag={mode:h.corner?'resize':'move',corner:h.corner,p0:p,o:{x:r.x,y:r.y,w:r.w,h:r.h}}; }
  else { sel=-1; drag={mode:'new',p0:p}; }
  draw(); ui();
};
cv.onpointermove=function(e){
  if(!D||!D.pages.length) return;
  var p=pt(e);
  if (!drag){ var h=hit(p); cv.style.cursor = !h ? 'crosshair' : h.corner ? CURSOR[h.corner] : 'move'; return; }
  var o=drag.o, dx=p.x-drag.p0.x, dy=p.y-drag.p0.y, r=regs()[sel];
  if (drag.mode==='new'){ pending={x:Math.min(drag.p0.x,p.x),y:Math.min(drag.p0.y,p.y),w:Math.abs(dx),h:Math.abs(dy)}; }
  else if (drag.mode==='move'){ r.x=Math.round(clamp(o.x+dx,0,cv.width-o.w)); r.y=Math.round(clamp(o.y+dy,0,cv.height-o.h)); }
  else {   // resize: 잡은 모서리만 움직이고 반대쪽 모서리는 고정
    var c=drag.corner, x1=o.x, y1=o.y, x2=o.x+o.w, y2=o.y+o.h;
    if (c[1]==='w') x1=clamp(o.x+dx,0,cv.width); else x2=clamp(x2+dx,0,cv.width);
    if (c[0]==='n') y1=clamp(o.y+dy,0,cv.height); else y2=clamp(y2+dy,0,cv.height);
    r.x=Math.round(Math.min(x1,x2)); r.y=Math.round(Math.min(y1,y2)); r.w=Math.max(5,Math.round(Math.abs(x2-x1))); r.h=Math.max(5,Math.round(Math.abs(y2-y1)));
  }
  draw();
};
cv.onpointerup=function(){ if(!drag) return;
  if (drag.mode==='new'){
    if (pending && (pending.w<5||pending.h<5)) pending=null;   // 클릭 수준은 무시
    if (pending){ ['x','y','w','h'].forEach(function(k){pending[k]=Math.round(pending[k]);}); }
  }
  drag=null; draw(); ui(); };

function applyMask(){
  if(!pending) return;
  pending.type=$('maskType').value; regs().push(pending); pending=null;
  sel=regs().length-1;   // 방금 만든 마스크를 선택 상태로 → 바로 이동/크기 조절 가능
  draw(); ui();
}
function delSel(){ if(sel<0) return; regs().splice(sel,1); sel=-1; draw(); ui(); }
function undo(){ regs().pop(); sel=-1; draw(); ui(); }
$('btnApply').onclick=applyMask;
$('btnDel').onclick=delSel;
$('maskType').onchange=function(){ if(D&&D.pages.length&&sel>=0){ regs()[sel].type=this.value; draw(); } };   // 선택된 마스크 종류 변경

/* ---------- 단축키 ----------
 * M / Enter: 선택 영역 마스킹 · Delete / Backspace: 선택 마스크 삭제 · 방향키: 1px 이동(Shift 10px)
 * Ctrl+Z: 마지막 마스킹 취소 · Esc: 선택 해제
 * 입력창·셀렉트에 포커스가 있거나 모달이 열려 있으면 동작하지 않음 */
var NUDGE={ArrowLeft:[-1,0],ArrowRight:[1,0],ArrowUp:[0,-1],ArrowDown:[0,1]};
function onKey(e){
  if(!root.isConnected||!D||!D.pages.length||busy||$('modal').classList.contains('emk-on')) return;
  if(/INPUT|TEXTAREA|SELECT/.test(e.target.tagName)||e.target.isContentEditable) return;
  var k=e.key;
  if (pending && (k==='m'||k==='M'||(k==='Enter'&&e.target.tagName!=='BUTTON'))){ e.preventDefault(); applyMask(); return; }
  if (k==='Escape' && (pending||sel>=0)){ pending=null; sel=-1; draw(); ui(); return; }
  if ((e.ctrlKey||e.metaKey) && (k==='z'||k==='Z') && regs().length){ e.preventDefault(); undo(); return; }
  if (sel<0) return;
  if (k==='Delete'||k==='Backspace'){ e.preventDefault(); delSel(); return; }
  var mv=NUDGE[k]; if(!mv) return;
  e.preventDefault();
  var st=e.shiftKey?10:1, r=regs()[sel];
  r.x=clamp(r.x+mv[0]*st,0,cv.width-r.w); r.y=clamp(r.y+mv[1]*st,0,cv.height-r.h); draw();
}
document.addEventListener('keydown',onKey);
$('btnUndo').onclick=undo;
$('btnReset').onclick=function(){ if(confirm('모든 마스킹을 초기화하시겠습니까?')){ D.pages.forEach(function(p){p.regions=[];}); pending=null; sel=-1; draw(); ui(); setMsg('초기화되었습니다.'); } };
$('btnPrev').onclick=function(){ D.idx--; pending=null; sel=-1; draw(); ui(); };
$('btnNext').onclick=function(){ D.idx++; pending=null; sel=-1; draw(); ui(); };
$('btnZoomIn').onclick=function(){ zoom=Math.min(4,(zoom||cv.clientWidth/cv.width)+0.25); draw(); ui(); };
$('btnZoomOut').onclick=function(){ zoom=Math.max(0.1,(zoom||cv.clientWidth/cv.width)-0.25); draw(); ui(); };
$('btnFit').onclick=function(){ zoom=0; draw(); ui(); };

/* ============================================================
 * 저장: 최종 확인 → ECM API 요청
 * ============================================================ */

function exportPage(pg){ var c=document.createElement('canvas'); c.width=pg.src.width; c.height=pg.src.height; paint(c.getContext('2d'),pg,false); return c; }

/* PNG 저장: 현재 페이지의 마스킹본을 PC 로 내려받기 (ECM 과 무관) */
$('btnPng').onclick=function(){ exportPage(D.pages[D.idx]).toBlob(function(b){
  var a=document.createElement('a'); a.href=URL.createObjectURL(b); a.download=S.cur.fileName.replace(/\.[^.]+$/,'')+'_masked_'+(D.idx+1)+'.png'; a.click(); URL.revokeObjectURL(a.href); },'image/png'); };

/* ★ ECM 전송 공통 로직 (api.save 호출) — 모달 버전/confirm 버전 모두 이걸 호출 */
function sendEcm(){
  var row=S.cur, fd=new FormData(), regions=[];
  ['procDate','procSeq','bizCd','fileSeq','fileName'].forEach(function(k){ fd.append(k,row[k]); });
  D.pages.forEach(function(pg,i){ regions.push({page:i+1,width:pg.src.width,height:pg.src.height,regions:pg.regions}); });
  fd.append('regions',JSON.stringify(regions));      // 서버에서 원본에 직접 마스킹하려는 경우 사용
  var jobs=D.pages.map(function(pg,i){ return new Promise(function(res){ exportPage(pg).toBlob(function(b){ fd.append('page_'+i+'.png',b,'page_'+i+'.png'); res(); },'image/png'); }); });
  busy=true; ui();
  return Promise.all(jobs).then(function(){ return postSave(fd); }).then(function(j){
    if (j.result!=='OK') throw new Error(j.message||'저장 실패');
    row.maskYn='Y'; row.maskDt=j.maskDt||nowStr();
    closeDoc(); $('curName').textContent=''; S.cur=null; renderList();
    setMsg('마스킹 저장이 완료되었습니다. ('+row.fileName+')','ok'); toast('ECM 저장 완료');
  }).catch(function(e){ alert('ECM 저장 실패: '+e.message); throw e; })
    .then(function(){ busy=false; ui(); }, function(e){ busy=false; ui(); throw e; });   // 실패 시 reject 유지 (호출측이 모달 유지 등 판단)
}
/* cfg.saveConfirm: 'modal'(미리보기+체크박스 모달) | 'confirm'(confirm 창 한 번 후 바로 전송) */
function saveByConfirm(){
  var np=D.pages.filter(function(p){return p.regions.length;}).length;
  if (!confirm('['+S.cur.fileName+']\n\n총 '+D.pages.length+'페이지 중 '+np+'페이지, 마스킹 영역 '+totalRegions()+'개\n\n마스킹본을 ECM에 저장합니다. 저장 후에는 되돌릴 수 없습니다.\n계속하시겠습니까?')) return;
  sendEcm().catch(function(){});   // 실패 알림은 sendEcm 내부에서 처리
}
$('cfChk').onchange=function(){ $('cfOk').disabled=!this.checked; };
$('cfCancel').onclick=function(){ $('modal').classList.remove('emk-on'); };
function saveByModal(){
  var pv=$('cfPreview'); pv.innerHTML='';
  /* 이미지는 1장, PDF 는 전체 페이지를 마스킹본으로 재생성하여 전송 */
  D.pages.forEach(function(pg,i){ var c=exportPage(pg);
    if (pg.regions.length){ var t=document.createElement('div'); t.className='emk-mut'; t.textContent=(i+1)+'페이지 (마스킹 '+pg.regions.length+'개)'; pv.appendChild(t); pv.appendChild(c); } });
  $('cfInfo').innerHTML='<b>'+esc(S.cur.fileName)+'</b> · 총 '+D.pages.length+'페이지 중 '+D.pages.filter(function(p){return p.regions.length;}).length+'페이지, 마스킹 영역 '+totalRegions()+'개';
  $('cfChk').checked=false; $('cfOk').disabled=true; $('modal').classList.add('emk-on');
}
$('btnSave').onclick=(cfg.saveConfirm==='confirm') ? saveByConfirm : saveByModal;
$('cfOk').onclick=function(){
  $('cfOk').disabled=true; $('cfOk').textContent='저장 중...';
  var done=function(){ $('cfOk').textContent='ECM 저장'; $('cfOk').disabled=!$('cfChk').checked; };
  sendEcm().then(function(){ $('modal').classList.remove('emk-on'); done(); }, done);
};

function onUnload(e){ if(dirty()){ e.preventDefault(); e.returnValue='저장하지 않은 마스킹 내용이 있습니다.'; } }
window.addEventListener('beforeunload',onUnload);

/* 주소 미입력 항목은 MOCK 으로 동작 — 어떤 항목이 MOCK 인지 안내 (주소를 넣으면 자동으로 사라짐) */
var mocks=['list','file','save'].filter(function(k){return !api[k];});
if (mocks.length) setMsg('[MOCK 동작 중: '+mocks.join(', ')+'] init 설정의 api 에 주소를 넣으면 실제 호출로 전환됩니다. 좌측에서 문서를 선택하세요.');

/* 초기 조회 */
$('fProcDate').value=cfg.defaultProcDate;
search();

return { destroy:function(){ document.removeEventListener('keydown',onKey); window.removeEventListener('beforeunload',onUnload); } };

}   // create

function todayStr(){ var d=new Date(),p=function(n){return n<10?'0'+n:n;}; return ''+d.getFullYear()+p(d.getMonth()+1)+p(d.getDate()); }

global.EcmMask={ init:function(root,cfg){ if(!root) throw new Error('EcmMask: root element not found'); return create(root,cfg); } };
})(window);
