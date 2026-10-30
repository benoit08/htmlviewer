package sample.ecmmask;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ECM 문서 마스킹 화면 + 화면(ecmMask.js)이 부르는 API 3개.
 * 응답 규격은 ecmMask.js 머리 주석과 같다. 오류는 HTTP 200 + {result:'FAIL', message} 로 돌려 화면이 사유를 표시한다.
 */
@Controller
public class EcmMaskController {

    private static final Logger logger = LoggerFactory.getLogger(EcmMaskController.class);

    @Resource(name = "ecmMaskService")
    private EcmMaskService ecmMaskService;

    @RequestMapping(value = "/")
    public String home() {
        return "redirect:/ecm/mask/view.do";
    }

    /** 메뉴 URL. EDMS 에서는 기존 레이아웃(tiles 등)의 본문에 ecmMask.jsp 를 넣는 것과 같다 */
    @RequestMapping(value = "/ecm/mask/view.do")
    public String view(ModelMap model) throws Exception {
        model.addAttribute("menus", ecmMaskService.selectMenuList());
        model.addAttribute("currentUrl", "/ecm/mask/view.do");
        model.addAttribute("contentPage", "/WEB-INF/views/ecm/ecmMask.jsp");
        return "layout";
    }

    /** 목록 조회 */
    @RequestMapping(value = "/ecm/mask/list.do")
    @ResponseBody
    public Map<String, Object> list(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<String, Object>();
        try {
            Map<String, Object> param = new HashMap<String, Object>();
            param.put("procDate", request.getParameter("procDate"));
            param.put("procSeq", request.getParameter("procSeq"));
            param.put("bizCd", request.getParameter("bizCd"));
            param.put("fileSeq", request.getParameter("fileSeq"));
            param.put("delYn", request.getParameter("delYn"));
            param.put("maskYn", request.getParameter("maskYn"));

            result.put("rows", ecmMaskService.selectEcmFileList(param));
            result.put("result", "OK");
        } catch (Exception e) {
            logger.error("ECM 목록 조회 오류", e);
            result.put("result", "FAIL");
            result.put("message", e.getMessage());
        }
        return result;
    }

    /** 문서 읽기 — 파일 바이너리를 그대로 내려준다 */
    @RequestMapping(value = "/ecm/mask/file.do")
    public void file(HttpServletRequest request, HttpServletResponse response) throws Exception {
        MaskFileCodec.Content content;
        try {
            content = ecmMaskService.selectEcmFileContent(keyParam(request));
        } catch (Exception e) {
            logger.error("ECM 문서 조회 오류", e);
            writeJson(response, fail(e));
            return;
        }

        response.setContentType(content.contentType);
        response.setContentLength(content.data.length);
        response.setHeader("Cache-Control", "no-store");   // 개인정보 원본 — 브라우저 캐시 금지
        OutputStream out = response.getOutputStream();
        try {
            out.write(content.data);
            out.flush();
        } finally {
            out.close();
        }
    }

    /** 마스킹 저장 (multipart: procDate, procSeq, fileSeq, regions, page_0.png, page_1.png ...) */
    @RequestMapping(value = "/ecm/mask/save.do", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, Object> save(MultipartHttpServletRequest request) {
        Map<String, Object> result = new HashMap<String, Object>();
        try {
            Map<String, Object> param = keyParam(request);
            param.put("regions", request.getParameter("regions"));
            param.put("userId", getLoginUserId(request));

            List<BufferedImage> pages = readPages(request);
            String maskDt = ecmMaskService.saveMask(param, pages);

            result.put("result", "OK");
            result.put("maskDt", maskDt);
        } catch (Exception e) {
            logger.error("ECM 마스킹 저장 오류", e);
            result = fail(e);
        }
        return result;
    }

    /** page_0.png, page_1.png ... 를 번호 순으로 읽는다 */
    private List<BufferedImage> readPages(MultipartHttpServletRequest request) throws Exception {
        TreeMap<Integer, MultipartFile> sorted = new TreeMap<Integer, MultipartFile>();
        Iterator<String> names = request.getFileNames();
        while (names.hasNext()) {
            String name = names.next();
            if (name.startsWith("page_") && name.endsWith(".png")) {
                Integer no = Integer.valueOf(name.substring(5, name.length() - 4));
                sorted.put(no, request.getFile(name));
            }
        }
        if (sorted.isEmpty()) {
            throw new IllegalArgumentException("마스킹 페이지 이미지가 없습니다.");
        }

        List<BufferedImage> pages = new ArrayList<BufferedImage>();
        for (MultipartFile file : sorted.values()) {
            InputStream in = file.getInputStream();
            try {
                BufferedImage img = ImageIO.read(in);
                if (img == null) {
                    throw new IllegalArgumentException("이미지를 읽을 수 없습니다: " + file.getName());
                }
                pages.add(img);
            } finally {
                in.close();
            }
        }
        return pages;
    }

    private Map<String, Object> keyParam(HttpServletRequest request) {
        Map<String, Object> param = new HashMap<String, Object>();
        param.put("procDate", request.getParameter("procDate"));
        param.put("procSeq", Long.valueOf(request.getParameter("procSeq")));
        param.put("fileSeq", Long.valueOf(request.getParameter("fileSeq")));
        return param;
    }

    /** 샘플은 고정값. EDMS 에서는 세션의 로그인 사용자 ID 로 바꾼다 */
    private String getLoginUserId(HttpServletRequest request) {
        return "sample";
    }

    private Map<String, Object> fail(Exception e) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("result", "FAIL");
        result.put("message", e.getMessage());
        return result;
    }

    private void writeJson(HttpServletResponse response, Map<String, Object> body) throws Exception {
        response.setContentType("application/json; charset=UTF-8");
        OutputStream out = response.getOutputStream();
        try {
            new ObjectMapper().writeValue(out, body);
        } finally {
            out.close();
        }
    }
}
