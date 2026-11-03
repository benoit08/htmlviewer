<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%--
  연습 과제: 원본 HTML 을 이 JSP 로 옮긴다.
    원본  http://localhost:8080/practice/mask-viewer.html   (src/main/resources/static/practice/mask-viewer.html)
    결과  http://localhost:8080/practice/view.do            (사이드바 "ECM파일처리내역 > JSP 변환 연습")
    이 파일은 저장하면 재기동 없이 새로고침으로 반영된다.

  컨트롤러(PracticeController)가 넘기는 값
    rows : 문서 목록 List<EcmFileVO>  →  ${r.procDate} ${r.procSeq} ${r.fileSeq} ${r.fileName} ${r.delYn} ${r.maskYn}
    이미지: ${ctx}/ecm/mask/file.do?procDate=...&procSeq=...&fileSeq=...
           (jpg · png 는 이미지 그대로, tif 1쪽은 png 로 온다. pdf · tif 여러 쪽은 PDF 라 <img> 로 못 연다)

  할 일
    1. <style> 내용을 옮긴다. 이 JSP 는 레이아웃(layout.jsp) 본문에 끼워지므로 <html> <head> <body> 는 빼고 내용만.
       body 에 준 스타일은 레이아웃 전체에 번지니 .mv 아래로만 한정한다.
    2. 본문 마크업(<div class="mv"> ... </div>)을 옮긴다.
    3. [변환 포인트 1] DOCS 배열을 rows 로 바꾼다. 방법은 둘 중 하나:
         a) <c:forEach> 로 JS 배열 원소를 찍는다   { procDate: '${r.procDate}', ... },
         b) 목록 <div class="mv-item"> 을 <c:forEach> 로 바로 그리고 data-* 속성에 키를 넣는다
       삭제건(delYn == 'Y')과 이미지로 못 여는 파일은 <c:if> 와 fn:endsWith 로 빼 본다.
    4. [변환 포인트 2] imageUrl(doc) 이 file.do 주소를 돌려주게 한다. 주소 앞에 contextPath 를 붙인다.
    5. [변환 포인트 3] <script> 를 옮긴다. 템플릿 리터럴 안의 ${...} 는 JSP 가 먼저 EL 로 읽는다.
       ${i} 처럼 EL 로 읽히는 식은 조용히 빈 값이 되고, ${...} 처럼 EL 문법이 아니면 페이지 전체가 500 이다 (JS 주석 안이어도).
       \${...} 로 막거나 문자열 + 연결로 바꾼다. (반대로 서버 값을 넣을 때는 ${...} 를 그대로 쓴다)
    6. 화면에 찍는 파일명은 <c:out> 이나 ${fn:escapeXml(...)} 로 감싼다 (HTML 이스케이프).
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>

<div style="padding:16px 16px 0">
  <b>JSP 변환 연습</b> — 서버에서 받은 문서 ${fn:length(rows)}건. 이 줄이 보이면 연결은 정상이다. 아래에 작성한다.
</div>

<%-- 1. 스타일 --%>

<style>
  body{margin:0;background:#f4f6fb}
  .mv{display:flex;gap:16px;padding:16px;font:13px/1.4 "Malgun Gothic",Arial,sans-serif;color:#222}
  .mv-list{width:280px;flex:none;border:1px solid #ccd;background:#fff;align-self:flex-start}
  .mv-list h3{margin:0;padding:10px 12px;border-bottom:1px solid #ccd;font-size:14px}
  .mv-item{padding:8px 12px;border-bottom:1px solid #eee;cursor:pointer}
  .mv-item:hover,.mv-item.on{background:#eef2ff}
  .mv-item small{color:#667}
  .mv-main{flex:1;min-width:0}
  .mv-tools{display:flex;gap:6px;align-items:center;margin-bottom:8px}
  .mv-tools span{margin-left:8px;color:#556}
  .mv-stage{border:1px solid #ccd;background:#888;overflow:auto;max-height:78vh}
  .mv-stage canvas{display:block;max-width:100%;cursor:crosshair}
</style>
<%-- 2. 마크업 --%>

<div class="mv">
  <div class="mv-list">
    <h3>문서 목록</h3>
    <div id="mvList"></div>
  </div>
  <div class="mv-main">
    <div class="mv-tools">
      <button type="button" id="mvUndo">되돌리기</button>
      <button type="button" id="mvClear">모두 지우기</button>
      <button type="button" id="mvDownload">PNG 저장</button>
      <span id="mvMsg">왼쪽에서 문서를 고르세요.</span>
    </div>
    <div class="mv-stage"><canvas id="mvCanvas" width="600" height="400"></canvas></div>
  </div>
</div>
<%-- 3 ~ 5. 스크립트 --%>
<script>
(function () {
  'use strict';

  // [변환 포인트 1] 목록 데이터 — 서버(rows)가 JS 배열을 찍는다. 삭제건과 <img> 로 못 여는 파일(pdf, tif)은 뺀다
  var DOCS = [
<c:forEach var="r" items="${rows}">
  <c:if test="${r.delYn ne 'Y' and (fn:endsWith(r.fileName, '.jpg') or fn:endsWith(r.fileName, '.png'))}">
    { procDate: '${r.procDate}', procSeq: ${r.procSeq}, fileSeq: ${r.fileSeq}, fileName: '${fn:escapeXml(r.fileName)}' },
  </c:if>
</c:forEach>
  ];

  // [변환 포인트 2] 이미지 주소 — file.do 가 XVARM 에서 읽어 내려준다. 앞에 contextPath(ctx)
  var CTX = '${ctx}';
  function imageUrl(doc) {
    return CTX + '/ecm/mask/file.do?procDate=' + doc.procDate + '&procSeq=' + doc.procSeq + '&fileSeq=' + doc.fileSeq;
  }

  var list = document.getElementById('mvList');
  var canvas = document.getElementById('mvCanvas');
  var g = canvas.getContext('2d');
  var cur = null, img = null, boxes = [], drag = null;

  function msg(t) { document.getElementById('mvMsg').textContent = t; }

  function renderList() {
    var html = '';
    DOCS.forEach(function (d, i) {
      // [변환 포인트 3] 템플릿 리터럴의 \${...} 는 JSP 가 EL 로 먼저 해석한다 → 앞에 \ 를 붙여 막는다
      html += `<div class="mv-item" data-i="\${i}">\${d.fileName}<br><small>\${d.procDate} / \${d.procSeq} / \${d.fileSeq}</small></div>`;
    });
    list.innerHTML = html;
  }

  function open(doc, el) {
    var items = list.querySelectorAll('.mv-item');
    for (var i = 0; i < items.length; i++) items[i].classList.toggle('on', items[i] === el);
    var im = new Image();
    im.onload = function () {
      cur = doc; img = im; boxes = [];
      canvas.width = im.naturalWidth;
      canvas.height = im.naturalHeight;
      draw();
      msg(`\${doc.fileName} — 드래그해서 가릴 영역을 지정하세요.`);
    };
    im.onerror = function () { msg('이미지를 읽을 수 없습니다: ' + doc.fileName); };
    im.src = imageUrl(doc);
  }

  function draw() {
    if (!img) return;
    g.drawImage(img, 0, 0);
    g.fillStyle = '#000';
    boxes.forEach(function (b) { g.fillRect(b.x, b.y, b.w, b.h); });
    if (drag) {
      g.strokeStyle = '#e53935';
      g.lineWidth = 2;
      g.strokeRect(drag.x, drag.y, drag.w, drag.h);
    }
  }

  // 화면에 줄여 보인 canvas 의 마우스 좌표 → 원본 픽셀 좌표
  function pos(e) {
    var r = canvas.getBoundingClientRect();
    return { x: (e.clientX - r.left) * canvas.width / r.width, y: (e.clientY - r.top) * canvas.height / r.height };
  }

  list.onclick = function (e) {
    var el = e.target.closest('.mv-item');
    if (el) open(DOCS[el.getAttribute('data-i')], el);
  };

  canvas.onmousedown = function (e) {
    if (!img) return;
    var p = pos(e);
    drag = { x: p.x, y: p.y, w: 0, h: 0, sx: p.x, sy: p.y };
  };

  window.addEventListener('mousemove', function (e) {
    if (!drag) return;
    var p = pos(e);
    drag.x = Math.min(drag.sx, p.x); drag.y = Math.min(drag.sy, p.y);
    drag.w = Math.abs(p.x - drag.sx); drag.h = Math.abs(p.y - drag.sy);
    draw();
  });

  window.addEventListener('mouseup', function () {
    if (!drag) return;
    if (drag.w > 3 && drag.h > 3) boxes.push({ x: drag.x, y: drag.y, w: drag.w, h: drag.h });
    drag = null;
    draw();
    msg(`가린 영역 \${boxes.length}개`);
  });

  document.getElementById('mvUndo').onclick = function () { boxes.pop(); draw(); };
  document.getElementById('mvClear').onclick = function () { boxes = []; draw(); };
  document.getElementById('mvDownload').onclick = function () {
    if (!img) { msg('먼저 문서를 여세요.'); return; }
    var a = document.createElement('a');
    a.href = canvas.toDataURL('image/png');
    a.download = cur.fileName.replace(/\.\w+$/, '') + '_masked.png';
    a.click();
  };

  // 가짜 개인정보 문서 한 장 (모든 값은 가상)
  function mockImage(title) {
    var c = document.createElement('canvas'); c.width = 620; c.height = 877;
    var x = c.getContext('2d');
    x.fillStyle = '#fff'; x.fillRect(0, 0, c.width, c.height);
    x.fillStyle = '#222'; x.font = 'bold 26px sans-serif'; x.fillText(title, 60, 90);
    x.font = '17px sans-serif';
    ['성명: 홍길동', '주민등록번호: 800101-1234567', '주소: 서울특별시 서초구 서초대로 000',
     '전화번호: 010-1234-5678', '계좌번호: 123456-01-234567'].forEach(function (t, i) { x.fillText(t, 70, 170 + i * 42); });
    x.strokeRect(50, 130, 520, 240);
    x.font = '13px sans-serif'; x.fillText('※ 샘플 문서 — 모든 정보는 가상입니다.', 60, 830);
    return c.toDataURL('image/png');
  }

  renderList();
})();
</script>
