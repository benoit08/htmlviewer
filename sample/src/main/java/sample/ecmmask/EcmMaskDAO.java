package sample.ecmmask;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.stereotype.Repository;

/**
 * ECM 마스킹 DAO. SQL 은 mapper/EcmMaskMapper.xml (namespace "ecmMask").
 * EDMS 의 공통 DAO(EgovAbstractMapper 등)가 있으면 그것을 상속해 sqlSession 부분만 바꾸면 된다.
 */
@Repository("ecmMaskDAO")
public class EcmMaskDAO {

    @Resource(name = "sqlSessionTemplate")
    private SqlSessionTemplate sqlSession;

    public List<EcmFileVO> selectEcmFileList(Map<String, Object> param) {
        return sqlSession.selectList("ecmMask.selectEcmFileList", param);
    }

    public EcmFileVO selectEcmFile(Map<String, Object> param) {
        return (EcmFileVO) sqlSession.selectOne("ecmMask.selectEcmFile", param);
    }

    public int updateMask(Map<String, Object> param) {
        return sqlSession.update("ecmMask.updateMask", param);
    }

    /* ---------- 샘플 전용 ---------- */

    public int insertEcmFile(EcmFileVO vo) {
        return sqlSession.insert("ecmMask.insertEcmFile", vo);
    }

    public int selectEcmFileCount() {
        Integer cnt = (Integer) sqlSession.selectOne("ecmMask.selectEcmFileCount");
        return cnt == null ? 0 : cnt.intValue();
    }

    public List<Map<String, Object>> selectMenuList() {
        return sqlSession.selectList("ecmMask.selectMenuList");
    }
}
