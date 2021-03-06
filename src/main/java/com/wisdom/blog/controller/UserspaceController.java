package com.wisdom.blog.controller;

import com.alibaba.fastjson.JSON;
import com.wisdom.blog.domain.Blog;
import com.wisdom.blog.domain.Catalog;
import com.wisdom.blog.domain.User;
import com.wisdom.blog.domain.Vote;
import com.wisdom.blog.service.BlogService;
import com.wisdom.blog.service.CatalogService;
import com.wisdom.blog.service.UserService;
import com.wisdom.blog.util.ConstraintViolationExceptionHandler;
import com.wisdom.blog.vo.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.validation.ConstraintViolationException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Controller
@RequestMapping("/u")
public class UserspaceController {

    @Autowired
    private BlogService blogService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private CatalogService catalogService;

    private static Logger logger = LoggerFactory.getLogger(UserspaceController.class);

    @GetMapping("/{username}")
    public String userSpace(@PathVariable("username") String username,Model model){

        User user  = (User) userDetailsService.loadUserByUsername(username);
        model.addAttribute("user",user);
        return "redirect:/u/" + username + "/blogs";
    }

    @GetMapping("/{username}/blogs")
    public String listBlogsByOrder(@PathVariable("username")String username,
                                   @RequestParam(value = "order",required = false,defaultValue = "new") String order,
                                   @RequestParam(value = "catalog",required = false) Long catalogId,
                                   @RequestParam(value = "keyword",required = false,defaultValue = "") String keyword,
                                   @RequestParam(value = "async",required = false) boolean async,
                                   @RequestParam(value = "pageIndex",required = false,defaultValue = "0") int pageIndex,
                                   @RequestParam(value = "pageSize",required = false,defaultValue = "10") int pageSize,
                                   Model model){

        User user = (User) userDetailsService.loadUserByUsername(username);
        Page<Blog> page = null;
        if (catalogId != null && catalogId > 0) {
            Catalog catalog = catalogService.getCatalogById(catalogId);
            Pageable pageable = new PageRequest(pageIndex,pageSize);
            page = blogService.listBlogsByCatalog(catalog, pageable);
            order = "";
        }
        if (order.equals("hot")) { // 最热查询
            Sort sort = new Sort(Sort.Direction.DESC,"readSize","commentSize","voteSize");
            Pageable pageable = new PageRequest(pageIndex, pageSize, sort);
            page = blogService.listBlogsByTitleLikeAndSort(user, keyword, pageable);
        }
        if (order.equals("new")) { // 最新查询
            Pageable pageable = new PageRequest(pageIndex, pageSize);
            page = blogService.listBlogsByTitleLike(user, keyword, pageable);
        }
        List<Blog> list = page.getContent();	// 当前所在页面数据列表

        model.addAttribute("user", user);
        model.addAttribute("order", order);
        model.addAttribute("catalogId",catalogId);
        model.addAttribute("keyword",keyword);
        model.addAttribute("page", page);
        model.addAttribute("blogList", list);
        return (async == true ? "userspace/u :: #mainContaierRepleace" : "userspace/u");
    }

    @GetMapping("/{username}/blogs/{id}")
    public String getBlogById(@PathVariable("username") String username,@PathVariable("id") Long id,Model model){
        User principal = null;
        blogService.readingIncrease(id);
        Blog blog = blogService.getBlogById(id);
        boolean isBlogOwner = false;

        if(SecurityContextHolder.getContext().getAuthentication() != null && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()
                && !SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString().equals("anonymousUser")){
            principal = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if(principal != null && username.equals(principal.getUsername())){
                isBlogOwner = true;
            }
        }

        List<Vote> votes = blog.getVotes();
        Vote currentVote = null;
        if(principal != null){
            for (Vote vote : votes){
                vote.getUser().getUsername().equals(principal.getUsername());
                currentVote = vote;
                break;
            }
        }

        model.addAttribute("currentVote",currentVote);
        model.addAttribute("isBlogOwner", isBlogOwner);
        model.addAttribute("blogModel",blog);

        return "userspace/blog";
    }

    @DeleteMapping("/{username}/blogs/{id}")
    public ResponseEntity<Response> deleteBlog(@PathVariable("username") String username,@PathVariable("id")Long id){
        try {
            blogService.removeBlog(id);
        }catch (Exception e){
            return ResponseEntity.ok(new Response(false,e.getMessage()));
        }

        String redirectUrl = "/u/" + username + "/blogs";
        return ResponseEntity.ok(new Response(true,"处理成功",redirectUrl));
    }

    @GetMapping("/{username}/blogs/edit")
    public ModelAndView createBlog(@PathVariable("username")String username, Model model) {
        User user = (User) userDetailsService.loadUserByUsername(username);
        List<Catalog> catalogs = catalogService.listCatalogs(user);

        model.addAttribute("blog", new Blog(null, null, null));
        model.addAttribute("catalogs",catalogs);
        return new ModelAndView("userspace/blogedit", "blogModel", model);
    }

    /**
     * 获取编辑博客的界面
     * @param model
     * @return
     */
    @GetMapping("/{username}/blogs/edit/{id}")
    public ModelAndView editBlog(@PathVariable("username") String username,@PathVariable("id") Long id, Model model) {
        User user = (User) userDetailsService.loadUserByUsername(username);
        List<Catalog> catalogs = catalogService.listCatalogs(user);

        model.addAttribute("blog", blogService.getBlogById(id));
        model.addAttribute("catalogs", catalogs);
        return new ModelAndView("userspace/blogedit", "blogModel", model);
    }

    /**
     * 保存博客
     * @param username
     * @param blog
     * @return
     */
    @PostMapping("/{username}/blogs/edit")
    @PreAuthorize("authentication.name.equals(#username)")
    public ResponseEntity<Response> saveBlog(@PathVariable("username") String username, @RequestBody Blog blog) {
        logger.info("博主:" + username + "-"+ "博客内容:" + JSON.toJSONString(blog));

        // 对 Catalog 进行空处理
        if (blog.getCatalog() == null) {
            return ResponseEntity.ok().body(new Response(false,"未选择分类"));
        }

        try {
            if(blog.getId() != null){
                Blog originalBlog = blogService.getBlogById(blog.getId());
                originalBlog.setTitle(blog.getTitle());
                originalBlog.setContent(blog.getContent());
                originalBlog.setSummary(blog.getSummary());
                originalBlog.setCatalog(blog.getCatalog());
                originalBlog.setTags(blog.getTags());
                blogService.saveBlog(originalBlog);
            }else {
                User user = (User)userDetailsService.loadUserByUsername(username);
                blog.setUser(user);
                blogService.saveBlog(blog);
            }
        } catch (ConstraintViolationException e)  {
            return ResponseEntity.ok().body(new Response(false, ConstraintViolationExceptionHandler.getMessage(e)));
        } catch (Exception e) {
            return ResponseEntity.ok().body(new Response(false, e.getMessage()));
        }

        String redirectUrl = "/u/" + username + "/blogs/" + blog.getId();
        return ResponseEntity.ok().body(new Response(true, "处理成功", redirectUrl));
    }

    @GetMapping("/{username}/profile")
    @PreAuthorize("authentication.name.equals(#username)")
    public ModelAndView profile(@PathVariable(name = "username") String username, Model model){
        User user = (User) userDetailsService.loadUserByUsername(username);
        model.addAttribute("user",user);
        return new ModelAndView("userspace/profile","userModel",model);
    }

    /**
     * 保存个人设置
     * @param username
     * @param user
     * @return
     */
    @PostMapping("/{username}/profile")
    @PreAuthorize("authentication.name.equals(#username)")
    public String saveProfile(@PathVariable(name = "username")String username,User user){
        User originalUser = userService.getUserById(user.getId());
        originalUser.setEmail(user.getEmail());
        originalUser.setName(user.getName());

        String rawPassword = originalUser.getPassword();
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        String encodePassword = encoder.encode(user.getPassword());
        boolean isMatche = encoder.matches(rawPassword, encodePassword);
        if(!isMatche){
            user.setEncodePassword(user.getPassword());
        }

        userService.saveUser(originalUser);
        return "redirect:/u/" + username + "/profile";
    }

    @GetMapping("/{username}/avatar")
    @PreAuthorize("authentication.name.equals(#username)")
    public ModelAndView avatar(@PathVariable("username")String username,Model model){
        User user = (User) userDetailsService.loadUserByUsername(username);
        model.addAttribute("user",user);
        return new ModelAndView("userspace/avatar","userModel",model);
    }

    @PostMapping("/{username}/avatar")
    @PreAuthorize("authentication.name.equals(#username)")
    public ResponseEntity<Response> savaAvatar(@PathVariable("username")String username, @RequestBody User user){
        String avatarUrl = user.getAvatar();
        User originaUser = userService.getUserById(user.getId());
        originaUser.setAvatar(avatarUrl);
        userService.saveUser(originaUser);

        return ResponseEntity.ok().body(new Response(true,"处理成功",avatarUrl));
    }

    @PostMapping("/{username}/avatarupload")
    @PreAuthorize("authentication.name.equals(#username)")
    @ResponseBody
    public String imgUpload(@PathVariable String username, MultipartFile upFile){
        String imgPath = "";
        InputStream ins = null;
        FileOutputStream fis = null;
        try {
            ins = upFile.getInputStream();
            String fileName = "avatar_" + username + "_" + System.currentTimeMillis();
            File upLoadDir = new File("avatarimage");
            if(!upLoadDir.exists()){
                upLoadDir.mkdir();
            }
            File file = new File(upLoadDir.getName() + File.separator + fileName);
            fis = new FileOutputStream(file);
            byte[] b = new byte[1024];
            int len = 0;
            while ((len = ins.read(b)) != -1 ){
                fis.write(b,0,len);
            }
            fis.flush();
            imgPath = "/api/asset/avatarimage/" + fileName;
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            try {
                ins.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                fis.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return imgPath;
    }
}
