package sample.ecmmask;

import java.util.HashMap;

import javax.annotation.Resource;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * JSP 변환 연습 화면 (샘플 전용, EDMS 로 옮기지 않음).
 * 원본 HTML: /practice/mask-viewer.html → 작성할 JSP: /WEB-INF/views/practice/practiceMask.jsp
 */
@Controller
public class PracticeController {

    @Resource(name = "ecmMaskService")
    private EcmMaskService ecmMaskService;

    @RequestMapping(value = "/practice/view.do")
    public String view(ModelMap model) throws Exception {
        model.addAttribute("rows", ecmMaskService.selectEcmFileList(new HashMap<String, Object>()));   // 조건 없이 전체
        model.addAttribute("menus", ecmMaskService.selectMenuList());
        model.addAttribute("currentUrl", "/practice/view.do");
        model.addAttribute("contentPage", "/WEB-INF/views/practice/practiceMask.jsp");
        return "layout";
    }
}
