# ECM 문서 마스킹 — 실행 샘플

H2 DB · Spring Boot 2.7(javax) · JSP · MyBatis로 화면부터 XVARM 저장까지 전체 흐름을 돌려 보는 샘플이다.
업무 코드(Controller · Service/Impl · DAO · VO · 매퍼 XML)는 EDMS에 옮기기 쉽도록 기존 Spring MVC 방식(`@Resource` 필드 주입, `@RequestMapping`, HashMap 파라미터, Java 6/7 문법)으로 작성했다.
Spring Boot는 샘플을 실행하는 껍데기(`SampleApplication`, `SampleDataLoader`)로만 쓰며 EDMS로 옮기지 않는다.
프런트 산출물 `../ecm-mask`를 복사하지 않고 그대로 쓰므로, 이 샘플이 동작하면 산출물도 검증된 것이다.

## 실행

```powershell
cd sample
.\mvnw.cmd spring-boot:run        # Maven 설치 불필요 (처음 실행 시 내려받음), JDK 9+ 권장
```

- 화면: http://localhost:8080 → 사이드바 "ECM파일처리내역 > ECM 문서 마스킹"
- DB 콘솔: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:file:./data/h2/ecm;MODE=Oracle;AUTO_SERVER=TRUE`, 사용자 `sa`)
- 처음 기동하면 가상 개인정보 문서 6건(jpg, png, pdf 2쪽, tif 2쪽, tif 1쪽, 삭제건)을 만들어 넣는다. 처음 상태로 돌리려면 `sample/data` 폴더를 지우고 다시 기동한다.
- 테스트: `.\mvnw.cmd test` (저장 시 키 교체, 원본 보관, 재마스킹 정리, TIF/PDF 변환)

## 구성

| 파일 | EDMS에 옮길 때 |
|---|---|
| `EcmMaskController` | `@RequestMapping`의 `list.do`, `file.do`, `save.do`, 화면 진입 `view.do`. 사용자 ID만 세션 값으로 바꾼다 |
| `EcmMaskService` / `EcmMaskServiceImpl` | 그대로 옮긴다. 저장 순서는 "새로 등록 → DB 갱신 → 옛 요소 정리"이다 |
| `EcmMaskDAO` / `EcmFileVO` | SqlSessionTemplate 기반이다. EDMS 공통 DAO가 있으면 그것을 상속하도록 바꾼다 |
| `MaskFileCodec` | 그대로 옮긴다. TIF는 PNG/PDF로 바꿔 보여 주고, 저장할 때 원래 형식으로 되돌린다 (PDFBox 필요) |
| `mapper/EcmMaskMapper.xml` | namespace `ecmMask`. 테이블과 컬럼명만 실제 것으로 바꾼다 (Oracle 문법) |
| `xvarm/*` | EDMS에 `XVarmAPI`가 이미 있으면 `XvarmClient` 구현 하나로 감싸 쓰고, 없으면 그대로 옮긴다 |
| `SampleDataLoader`, `layout.jsp`, `schema.sql`, `data.sql` | 샘플 전용이라 옮기지 않는다 |

## XVARM

| `xvarm.mode` | 동작 |
|---|---|
| `local` (기본) | 엔진 없이 `data/xvarm` 폴더에 저장한다. shortId는 실제처럼 16자리로 만든다 |
| `inzent` | 실제 엔진에 연결한다. `com.windfire.apis.*`를 리플렉션으로 호출하므로 jar 없이도 빌드된다 |

> **라이선스 주의** — XVARM jar(`xtormapi_*.jar`, `xvarmapi*.jar`)는 라이선스 대상 고객사 전용이다.
> 이 프로젝트의 소스, `lib`, 빌드 의존성, war에 **넣지 않는다**. 루트 `.gitignore`가 `*.jar`를 막는다.
> 테스트할 때만 프로젝트 밖 경로를 실행 인자로 준다.

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--xvarm.mode=inzent --xvarm.jar-path=D:/라이선스경로/xtormapi_20210723.jar --xvarm.ip=<엔진IP> --xvarm.user=<계정> --xvarm.password=<비밀번호>"
```

엔진에 연결할 수 없으면 화면에 `[XVARM 11] ... Connection refused`가 표시된다. jar 연동은 정상이고 접속만 실패한 상태다.
