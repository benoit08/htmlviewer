<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%-- 샘플용 공통 레이아웃 (EDMS 의 사이드바 + 본문 영역에 해당). 메뉴는 TB_MENU 에서 읽음 --%>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>EDMS 샘플 — ECM 문서 마스킹</title>
<style>
  body{margin:0;display:flex;min-height:100vh;font:13px/1.4 "Malgun Gothic",Arial,sans-serif;background:#f4f6fb}
  .side{width:220px;flex:none;background:linear-gradient(180deg,#4e6fe0,#3451c7);color:#fff;padding:16px 0}
  .side .logo{font-size:16px;font-weight:700;padding:0 20px 16px;border-bottom:1px solid rgba(255,255,255,.2)}
  .side .grp{padding:14px 20px 6px;font-weight:600;border-top:1px solid rgba(255,255,255,.12)}
  .side a{display:block;padding:7px 20px 7px 34px;color:rgba(255,255,255,.85);text-decoration:none}
  .side a:hover,.side a.on{background:rgba(255,255,255,.15);color:#fff}
  .content{flex:1;min-width:0}
</style>
</head>
<body>
<aside class="side">
  <div class="logo">EDMS (샘플)</div>
  <c:forEach var="m" items="${menus}">
    <c:if test="${empty m.PARENT_ID}">
      <div class="grp">${m.MENU_NM}</div>
      <c:forEach var="s" items="${menus}">
        <c:if test="${s.PARENT_ID eq m.MENU_ID}">
          <a href="${empty s.MENU_URL ? '#' : ctx.concat(s.MENU_URL)}" class="${s.MENU_URL eq currentUrl ? 'on' : ''}">${s.MENU_NM}</a>
        </c:if>
      </c:forEach>
    </c:if>
  </c:forEach>
</aside>
<main class="content">
  <jsp:include page="${contentPage}"/>
</main>
</body>
</html>
