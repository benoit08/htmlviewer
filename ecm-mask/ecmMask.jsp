<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%--
  ECM 문서 마스킹 — 메뉴 화면 본문(fragment). 헤더/사이드바는 기존 공통 레이아웃(tiles/include 등)에 맡김.

  배포 구조 (정적 리소스 폴더는 환경에 맞게 변경)
    /resources/ecm-mask/ecmMask.css, ecmMask.skin.css, ecmMask.js
    /resources/ecm-mask/lib/pdfjs/pdf.min.js, pdf.worker.min.js
    ecmMask.body.jspf  → 이 JSP 와 같은 폴더 (정적 include 용)

  주의: ecmMask.js / body.jspf 안에는 EL·스크립틀릿이 없어 JSP 가 건드리지 않음.
        서버 값은 아래 init 설정에서만 주입함.
--%>
<%
  String ctx = request.getContextPath();
  String res = ctx + "/resources/ecm-mask";     // 정적 리소스 경로
%>
<link rel="stylesheet" href="<%= res %>/ecmMask.css">
<%-- 기본 외관. 사내 CSS 를 입힐 때는 아래 한 줄 삭제 --%>
<link rel="stylesheet" href="<%= res %>/ecmMask.skin.css">

<%@ include file="ecmMask.body.jspf" %>

<script src="<%= res %>/lib/pdfjs/pdf.min.js"></script>
<script src="<%= res %>/ecmMask.js"></script>
<script>
(function () {
  window.ecmMaskHandle = EcmMask.init(document.getElementById('ecmMask'), {
    api: {                                   // ★ 빈 값('')으로 두면 그 항목은 MOCK 으로 동작
      list: '<%= ctx %>/ecm/mask/list.do',   // 샘플(sample/) 컨트롤러와 같은 주소
      file: '<%= ctx %>/ecm/mask/file.do',
      save: '<%= ctx %>/ecm/mask/save.do'
    },
    csrf: { header: '', token: '' },         // 예) { header: 'X-CSRF-TOKEN', token: '서버에서 발급한 토큰' }
    fileMethod: 'GET',                       // 'GET' | 'POST'
    pdfWorker: '<%= res %>/lib/pdfjs/pdf.worker.min.js',
    saveConfirm: 'modal'                     // 'modal' = 미리보기 모달+체크박스 / 'confirm' = confirm 한 번 후 바로 전송
  });
  // ajax 로 화면을 교체하는 구조라면 화면 전환 전에: window.ecmMaskHandle.destroy();
})();
</script>
