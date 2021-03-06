package com.blogging.aps.business;

import com.blogging.aps.model.dto.*;
import com.blogging.aps.model.entity.Response;
import com.blogging.aps.model.entity.post.PassageEntity;
import com.blogging.aps.model.entity.post.PostInfoEntity;
import com.blogging.aps.model.entity.post.TagEntity;
import com.blogging.aps.model.entity.post.TagRelationEntity;
import com.blogging.aps.model.entity.post.PostAddReqEntity;
import com.blogging.aps.service.TagService;
import com.blogging.aps.service.post.PostService;
import com.blogging.aps.support.utils.DateUtils;
import com.blogging.aps.support.utils.IdGenerator;
import com.blogging.aps.support.utils.MarkDownUtils;
import com.blogging.aps.support.utils.ResponseBuilder;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author techoneduan
 * @date 2019/1/18
 */

@Component
public class PostBusiness {

    private static final Logger LOG = LoggerFactory.getLogger(PostBusiness.class);

    @Autowired
    private PostService postService;

    @Autowired
    private TagService tagService;


    /**
     * 添加新博客
     *
     * @param entity
     * @return
     */
    public Response addPost(PostAddReqEntity entity) {
        PostInfoEntity postInfoEntity = new PostInfoEntity();
        PassageEntity passageEntity = new PassageEntity();
        if (StringUtils.isBlank(entity.getTitle()))
            return ResponseBuilder.build(false, "文章标题为空");
        buildArticle(postInfoEntity, passageEntity, entity);
        //插入文章
        postService.insertPassgae(passageEntity);
        postService.insertPost(postInfoEntity);
        //储存对应tag
        buildTags(entity.getTags(), postInfoEntity.getPostId());
        return ResponseBuilder.build(true, null);
    }

    /**
     * 首页文章查询
     * @return
     */
    public Response homepagePostQuery() {
        List<PostInfoEntity> postInfoEntities = postService.queryLatestFivePosts();
        if (postInfoEntities.size() == 0)
            return ResponseBuilder.build(true, null);
        List<HomePagePostListRespDTO> respDTOS = buildHomePagePostRespDTO(postInfoEntities);
        return ResponseBuilder.build(true, respDTOS);
    }

    /**
     * 查询博客展示信息
     * @param reqDTO
     * @return
     */
    public Response queryBlog(PostQueryReqDTO reqDTO){
        PostInfoEntity postInfoEntity = postService.queryPostByPostId(reqDTO.getPostId());
        if (null == postInfoEntity)
            return ResponseBuilder.build(true, "文章为空");
        PassageEntity passageEntity = postService.queryPassageByPassageId(postInfoEntity.getPassageId());
        String htmlContent = MarkDownUtils.markDownToHtml(passageEntity.getContent());

        PostInfoEntity previous = postService.queryPreviousPost(postInfoEntity.getAddTime(), reqDTO.getPostId());
        PostInfoEntity next = postService.queryNextPost(postInfoEntity.getAddTime(), reqDTO.getPostId());

        PostInfoDTO previousDTO = null, nextDTO = null;
        if (null != previous) {
            previousDTO = new PostInfoDTO() {
                {
                    setPostId(previous.getPostId());
                    setTitle(previous.getTitle());
                }
            };
        }
        if (null != next) {
            nextDTO = new PostInfoDTO() {
                {
                    setPostId(next.getPostId());
                    setTitle(next.getTitle());
                }
            };
        }
        List<String> tags = getPostTags(reqDTO.getPostId());
        BlogQueryRespDTO respDTO = new BlogQueryRespDTO() {
            {
                setPostId(reqDTO.getPostId());
                setTitle(postInfoEntity.getTitle());
                setHtmlContent(htmlContent);
                setAddTime(DateUtils.formatDate(postInfoEntity.getAddTime()));
                setTagList(tags);
            }
        };
        respDTO.setPreviousPost(previousDTO);
        respDTO.setNextPost(nextDTO);
        return ResponseBuilder.build(true, respDTO);
    }

    /**
     * 分页查询博客列表
     * @param queryDTO
     * @return
     */
    public Response postPagingQuery(PostPagingQueryDTO queryDTO){
        if(null == queryDTO)
            return ResponseBuilder.build(true,"分页查询条件为空");
        List<PostInfoEntity> entities = postService.queryPostListByPaging(queryDTO);
        List<HomePagePostListRespDTO> respDTOS = buildHomePagePostRespDTO(entities);
        return ResponseBuilder.build(true,respDTOS);
    }

    //TODO tag?

    private void buildArticle(PostInfoEntity postInfoEntity, PassageEntity passageEntity, PostAddReqEntity reqEntity) {
        postInfoEntity.setTitle(reqEntity.getTitle());
        postInfoEntity.setCategory(reqEntity.getCategory());
        postInfoEntity.setPostId(IdGenerator.generatePostId());
        postInfoEntity.setDelFlag(0);
        postInfoEntity.setMemberId(reqEntity.getMemberId());
        postInfoEntity.setSummary(reqEntity.getSummary());

        passageEntity.setPassageId(IdGenerator.generatePassageId());
        passageEntity.setDelFlag(0);
        passageEntity.setContent(reqEntity.getContent());

        Date now = new Date();
        postInfoEntity.setAddTime(now);
        postInfoEntity.setUpdateTime(now);
        passageEntity.setAddTime(now);
        passageEntity.setUpdateTime(now);

        postInfoEntity.setPassageId(passageEntity.getPassageId());

    }

    private void buildTags(List<String> tagList, String postId) {
        if (null == tagList || tagList.size() == 0)
            return;
        //去重
        tagList = tagList.stream().distinct().collect(Collectors.toList());
        tagList.forEach(item -> checkAndAddTag(item, postId));

    }


    //检查tag并添加对应关系
    private void checkAndAddTag(String tag, String postId) {
        List<TagEntity> tagEntityList = tagService.queryTagByName(tag);
        Integer tagId = null;
        if (null == tagEntityList || tagEntityList.size() == 0) {
            TagEntity tagEntity = new TagEntity() {
                {
                    setTagName(tag);
                    setDelFlag(0);
                    setAddTime(new Date());
                    setUpdateTime(new Date());

                }
            };
            tagService.insertTag(tagEntity);
            tagId = tagEntity.getId();
        }
        if (null == tagId) {
            tagId = tagEntityList.get(0).getId();
        }
        TagRelationEntity relationEntity = new TagRelationEntity() {
            {
                setPostId(postId);
                setDelFlag(0);
                setAddTime(new Date());
                setUpdateTime(new Date());
            }
        };
        relationEntity.setTagId(tagId);
        tagService.insertTagRelation(relationEntity);
    }

    private List<HomePagePostListRespDTO> buildHomePagePostRespDTO(List<PostInfoEntity> postInfoEntities) {
        List<HomePagePostListRespDTO> respDTOS = new ArrayList<>();
        for (PostInfoEntity postInfoEntity : postInfoEntities) {
            HomePagePostListRespDTO respDTO = new HomePagePostListRespDTO() {
                {
                    setPostId(postInfoEntity.getPostId());
                    setTitle(postInfoEntity.getTitle());
                    setSummary(postInfoEntity.getSummary());
                    setUpdateTime(DateUtils.formatDate(postInfoEntity.getUpdateTime()));
                }
            };
            List<String> tagList  =getPostTags(postInfoEntity.getPostId());
            //TODO 可以缓存tag列表
            if (null == tagList || tagList.size() == 0) {
                respDTO.setTagList(new ArrayList<>());
                respDTOS.add(respDTO);
            }
            respDTO.setTagList(tagList);
            respDTOS.add(respDTO);
        }
        return respDTOS;
    }

    private List<String> getPostTags(String postId) {

        List<Integer> tagIdList = tagService.queryByPostId(postId)
                .stream().map(item -> item.getTagId()).collect(Collectors.toList());
        if (null == tagIdList || tagIdList.size() == 0)
            return null;
        List<String> tagList = tagService.queryByTagIdList(tagIdList)
                .stream().map(item -> item.getTagName()).collect(Collectors.toList());
        return tagList;
    }

    /**
     * 查询文章md格式
     */

    public Response queryMDPost(PostQueryReqDTO reqDTO) {
        String postId = reqDTO.getPostId();
        PostInfoEntity postInfoEntity = postService.queryPostByPostId(postId);
        if (null == postInfoEntity)
            return ResponseBuilder.build(true,"文章为空");

        PassageEntity passageEntity = postService.queryPassageByPassageId(postInfoEntity.getPassageId());
        if (null == passageEntity)
            return ResponseBuilder.build(true,"文章为空");

        return ResponseBuilder.build(true,passageEntity.getContent());
    }
}
