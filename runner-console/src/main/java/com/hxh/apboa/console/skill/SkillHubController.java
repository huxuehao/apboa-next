package com.hxh.apboa.console.skill;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.hxh.apboa.common.config.auth.RoleNeed;
import com.hxh.apboa.common.consts.SysConst;
import com.hxh.apboa.common.enums.TenantRole;
import com.hxh.apboa.common.exception.BusinessException;
import com.hxh.apboa.common.r.R;
import com.hxh.apboa.common.util.ZipExtractUtils;
import com.hxh.apboa.common.vo.SkillFileTreeNodeVO;
import com.hxh.apboa.common.vo.SkillsHubVO;
import com.hxh.apboa.skill.imports.SkillImportPathResolver;
import com.hxh.apboa.skill.imports.SkillImportService;
import com.hxh.apboa.skill.imports.config.LocalImportConfig;
import com.hxh.apboa.skill.imports.config.UploadImportConfig;
import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/skill/hub")
@RequiredArgsConstructor
public class SkillHubController {
    private final SkillImportService skillImportService;
    @GetMapping("/download")
    @RoleNeed({TenantRole.TENANT_ADMIN, TenantRole.TENANT_EDITOR})
    public R<?> downloadSkill(@RequestParam("slug") String slug,@RequestParam(value = "category") String category)throws IOException{
        InputStream inputStream = null;
        Path downloadDir = null;
        Path extractDir = null;
        try {
            Path tempBase = Paths.get(SysConst.ROOT_DIR_NAME, "skillHub");
            Files.createDirectories(tempBase);
            Path tempZip = tempBase.resolve(IdUtil.getSnowflakeNextIdStr() + ".zip");

            extractDir = tempBase.resolve(IdUtil.getSnowflakeNextIdStr());
            Files.createDirectories(extractDir);

            downloadDir = tempBase.resolve(IdUtil.getSnowflakeNextIdStr());
            Files.createDirectories(downloadDir);

            File skillFile = Path.of(downloadDir.toString(),slug+".zip").toFile();
            HttpUtil.downloadFile("https://api.skillhub.cn/api/v1/download?slug="+slug,skillFile,30000);
            try {
                inputStream = FileUtil.getInputStream(skillFile);
                ZipExtractUtils.extractZipSafely(inputStream,Path.of(extractDir.toString(),slug), tempZip);
            } catch (IOException e) {
                throw new BusinessException("压缩包解压失败，请确认文件为有效 zip 格式: " + e.getMessage());
            }finally {
                if(inputStream!=null){
                    inputStream.close();
                }
                FileUtil.del(tempZip);
            }
            LocalImportConfig config = new LocalImportConfig();
            config.setPath(extractDir.toString());
            config.setCategory(category);
            config.setCover(false);
            return R.data(skillImportService.importFromLocal(config));
        }finally {
            FileUtil.del(downloadDir);
            FileUtil.del(extractDir);
        }

    }

    @GetMapping("/search")
    public R<List<SkillsHubVO>> skillsSearch(@RequestParam(required = false,value = "keyword") String keyword, @RequestParam(required = false,value = "category") String category, @RequestParam(required = false,value = "source") String source, @RequestParam(required = false,value = "labels") String labels, @RequestParam(required = false,value = "sortBy") String sortBy, @RequestParam(required = false,value = "order") String order, @RequestParam(required = false,value = "page") int page) throws Exception{
        List<SkillsHubVO> result = new ArrayList<>();
        Map<String, Object> paramMap = new HashMap<>();
        if(page <=0){
            page = 1;
        }
        paramMap.put("pageSize",30);
        paramMap.put("page",page);
        paramMap.put("keyword",keyword);
        paramMap.put("category",category);
        paramMap.put("source",source);
        // 只看免费、不需要 API Key 的

        paramMap.put("labels",labels);
        paramMap.put("sortBy",sortBy);
        paramMap.put("order",order);

        try (HttpResponse response = HttpRequest.get("https://api.skillhub.cn/api/skills").form(paramMap)
                .setConnectionTimeout(10000)
                .setReadTimeout(10000)
                .execute()) {
            if(response.isOk()){
                JSONObject data = new JSONObject(response.body());
                if(data.getInt("code")==0){
                    JSONArray skillsItems = data.getJSONObject("data").getJSONArray("skills");
                    for (int i = 0 ; i < skillsItems.length() ; i++){
                        JSONObject skill = skillsItems.getJSONObject(i);
                        SkillsHubVO skillsHubVO = new SkillsHubVO();
                        JSONObject labelsJson = skill.optJSONObject("labels");
                        if(labelsJson!=null && labelsJson.has("requires_api_key")){
                            skillsHubVO.setRequiresApiKey(labelsJson.getString("requires_api_key"));
                        }
                        skillsHubVO.setSlug(skill.getString("slug"));
                        skillsHubVO.setVersion(skill.getString("version"));
                        skillsHubVO.setName(skill.getString("name"));
                        skillsHubVO.setHomepage("https://skillhub.cn/skills/"+skillsHubVO.getSlug());
                        skillsHubVO.setIconUrl(skill.getString("iconUrl"));
                        skillsHubVO.setDescription(skill.getString("description_zh"));
                        skillsHubVO.setCategory(skill.getString("category"));
                        skillsHubVO.setDownloads(skill.getString("downloads"));
                        Timestamp createdAtTimestamp = new Timestamp(skill.getLong("created_at"));
                        skillsHubVO.setCreatedAt(createdAtTimestamp.toLocalDateTime());
                        Timestamp updatedAtTimestamp = new Timestamp(skill.getLong("updated_at"));
                        skillsHubVO.setUpdatedAt(updatedAtTimestamp.toLocalDateTime());
                        result.add(skillsHubVO);
                    }
                    return R.data(result);
                }
            }
            throw new BusinessException("请求skill hub 失败："+response.body());
        }
    }
}
