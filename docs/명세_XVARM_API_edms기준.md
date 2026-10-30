# XVARM(Inzent XTORM) 연동 API 명세 — edms 소스 기준 · 2026-10-29

> 근거: `exclude/원본/edms/src/main/java/minervasoft/edms/external/inzent/xvarm/XVarmAPI.java`,
> `common/webapp/Core.java`(설정 적재), `edms/service/EdmsServiceImpl.java`(호출), `resources/application.properties`.
> 별도 프로젝트에서 그대로 쓰기 위한 요약이다. PPR_AP 연동 설계는 `명세_ECM_XVARM_저장API.md` 를 본다.

## 1. 개요

- HTTP API 가 아니다. 벤더 jar 로 엔진 포트(TCP)에 직접 붙는다.
- 라이브러리: `WEB-INF/lib/xtormapi_20210723.jar` (`pom.xml` 좌표 `com.inzent:xvarmapi:1.0.0`)
- 쓰는 클래스는 두 개다: `com.windfire.apis.asysConnectData`(접속), `com.windfire.apis.asys.asysUsrElement`(파일 작업)
- 호출 1건마다 **접속 → 작업 1회 → close** 한다(풀 미사용).
- 결과코드: 벤더 메서드가 `int` 를 돌려준다. **0 = 성공**, 그 외 실패 → `usr.getLastError()` 로 메시지.

## 2. 설정

| 키(edms) | 예 | 쓰는 곳 |
|---|---|---|
| `ecm.config.ip` | 엔진 IP | 접속 |
| `ecm.config.port` | `2102` | 접속 |
| `ecm.config.id` / `ecm.config.pw` | `SUPER` / (비밀) | 접속 계정 |
| `ecm.config.desc` | `ECM Server` | 접속 클라이언트 설명 · 등록 설명(`m_descr`) |
| `ecm.config.gateway` | `XTORM_MAIN` | 등록 대상 · ElementID 앞부분 |
| `ecm.config.cclassid` | `HCIPPR_CC` | 등록 C-Class(보관 분류) |
| `ecm.config.eclassid` | `IMAGE` | 등록 E-Class · ElementID 뒷부분 |
| `ecm.config.sclassid` | `SUPER` | 등록 User S-Class(권한 분류) |

- edms 는 `xvarm.config.*` 를 먼저 읽다가 주석 처리하고 `ecm.config.*` 를 쓴다. IP 는 설정이 아니라 **서버 IP 별로 코드에 하드코딩**돼 있다(`Core.java` 424~431). 새 프로젝트는 설정으로 뺀다.
- 값(Gateway · Class ID · 계정)은 ECM 담당이 정해 준다.

## 3. ElementID

| 형태 | 형식 | 비고 |
|---|---|---|
| 짧은 ID | `2022110502062401` | 등록 결과 `usr.getShortID()`. **DB 에는 이것만 저장**(edms `ELEMENT_ID` · `ORG_ELEMENT_ID`) |
| 전체 ID | `{gateway}::{짧은 ID}::{eclassid}` | 조회 · 교체 · 삭제 때 `usr.m_elementId` 에 넣는다 |

E-Class 를 여러 개 쓰면 짧은 ID 와 함께 E-Class 도 저장해야 전체 ID 를 다시 만들 수 있다.

## 4. API

### 4.0 접속 / 종료
```java
asysConnectData con = new asysConnectData(ip, port, desc, id, pw);
...
con.close();
```

### 4.1 등록 — 파일
```java
asysUsrElement usr = new asysUsrElement(con);
usr.m_localFile  = filePath;      // 올릴 로컬 파일 전체 경로
usr.m_descr      = desc;
usr.m_cClassId   = cclassid;
usr.m_eClassId   = eclassid;
usr.m_userSClass = sclassid;
int ret = usr.create(gateway);    // 0 = 성공
String shortId = usr.getShortID();
```
edms 래퍼: `upload(filePath)`, `upload(filePath, cClass)`(C-Class 만 바꿈), `upload(filePath, vo)`(vo 는 쓰지 않음) → `XvarmResult{result, elementId(짧은 ID), error}`

### 4.2 등록 — 스트림
```java
int ret = usr.create(gateway, inputStream, "", "");   // 3·4번째 인자는 벤더 암호화용 — 빈 값
```
속성(`m_descr`·`m_cClassId`·`m_eClassId`·`m_userSClass`)은 4.1 과 같다. edms 래퍼 `uploadStream(stream, cClassId)` — 인자 `cClassId` 는 실제로 쓰지 않고 설정값을 넣는다(버그).

### 4.3 조회(다운로드) — 파일
```java
usr.m_elementId = gateway + "::" + shortId + "::" + eclassid;
int ret = usr.getContent(savePath);   // savePath = 저장할 파일 전체 경로
```
edms 래퍼: `download(shortId, downloadPath, fileName)` → `XvarmResult`

### 4.4 조회 — 스트림
```java
int ret = usr.getContent(outputStream, "", "");       // 2·3번째 인자는 벤더 복호화용 — 빈 값
```
edms 래퍼: `downloadStream(outputStream, shortId)` → `int`

### 4.5 교체(같은 ID 에 내용만 바꿈)
```java
usr.m_elementId = gateway + "::" + shortId + "::" + eclassid;
int ret = usr.replaceContent(filePath);
```
edms 래퍼: `replace(filePath, shortId)` → `XvarmResult`. ID 는 그대로다.

### 4.6 삭제
```java
usr.m_elementId = gateway + "::" + shortId + "::" + eclassid;
int ret = usr.delete();
```
edms 래퍼: `delete(shortId)` → `int`

## 5. edms 의 실제 사용 흐름

| 업무 | 호출 |
|---|---|
| 문서 등록(`EdmsServiceImpl` 2487 · 3092) | `upload` → 짧은 ID 를 `ELEMENT_ID`(가공본) / `ORG_ELEMENT_ID`(원본)에 저장 |
| 이미지 분할 저장(5986) | 분할 파일마다 `fileUpload` → `upload` |
| 열람 · 복사 · 다운로드 · Base64 응답(4992 · 5738 · 5938 · 6044 · 6099 · 6113) | `fileDownload` → 임시 폴더에 `download` → 응답 후 임시 파일 삭제 |

- **파일 암호화는 엔진이 아니라 앱이 한다**: `MCryptoConstants.enableyn=Y` 이면 올리기 전 `MCryptoModule.encFile2File`(`*_enc` 파일)로 암호화해 올리고, 받은 뒤 `decFile2File` 로 복호화한다(MagicJCrypto). 벤더 API 의 암호화 인자는 쓰지 않는다.
- 교체 · 삭제 · 스트림 메서드는 래퍼에만 있고 업무에서 부르지 않는다.
- `ViewerServiceImpl` 의 호출(299 · 487 · 639)은 반환형이 래퍼와 맞지 않고 import 도 없다 — 쓰지 않는 코드로 보고 제외했다.

## 6. 새 프로젝트에서 고칠 점

- `XVarmAPI` 가 `@Component`(싱글턴)인데 접속 객체 `con` 을 필드로 둔다 → 동시 호출 시 다른 요청의 접속을 닫는다. 메서드 지역변수로 둔다(edms 는 매번 `new XVarmAPI()` 로 피해 감).
- `upload(filePath)` · `replace` 등은 예외 때 `error` 를 비워 둔다 → 예외 메시지를 결과에 담는다.
- 접속 계정 비밀번호를 디버그 로그로 찍는다(`showConfigLog`, `Core` 의 `ENGINEPW` 로그) → 남기지 않는다.
- 같은 내용의 `upload` 3벌 → `upload(filePath, cClassId)` 하나로 합친다.
