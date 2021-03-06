/*!
 * blogedit.html 页面脚本.
 * 
 * @since: 1.0.0 2017-03-26
 * @author Way Lau <https://waylau.com>
 */
"use strict";
//# sourceURL=blogedit.js

// DOM 加载完再执行
$(function() {
    var ue = UE.getEditor('editor');

    UE.Editor.prototype._bkGetActionUrl = UE.Editor.prototype.getActionUrl;
    UE.Editor.prototype.getActionUrl = function(action) {
        if (action == 'uploadimage' || action == 'uploadscrawl' || action == 'uploadimage') {
            return '/ueditor/imgupload';
            //'http://localhost:8080/imgUpload';为方法imgUpload的访问地址
        } else {
            return this._bkGetActionUrl.call(this, action);
        }
    }
  
    // 初始化标签控件
    $('.form-control-tag').tagEditor({
        initialTags: [],
        maxTags: 5,
        delimiter: ', ',
        forceLowercase: false,
        animateDelete: 0,
        placeholder: '请输入标签'
    });
    
    $('.form-control-chosen').chosen();

 	// 发布博客
 	$("#submitBlog").click(function() {

		// 获取 CSRF Token 
		var csrfToken = $("meta[name='_csrf']").attr("content");
		var csrfHeader = $("meta[name='_csrf_header']").attr("content");

		var content = ue.getContent();
		$.ajax({
		    url: '/u/'+ $(this).attr("userName") + '/blogs/edit',
		    type: 'POST',
			contentType: "application/json; charset=utf-8",
		    data:JSON.stringify({
				"id":$('#blogId').val(),
		    	"title": $('#title').val(), 
		    	"summary": $('#summary').val() ,
				"catalog":{"id":$('#catalogSelect').val()},
		    	"content": content,
				"tags":$('.form-control-tag').val()}),
			beforeSend: function(request) {
			    request.setRequestHeader(csrfHeader, csrfToken); // 添加  CSRF Token 
			},
			 success: function(data){
				 if (data.success) {
					// 成功后，重定向
					 window.location = data.body;
				 } else {
					 toastr.error("error!"+data.message);
				 }
				 
		     },
		     error : function() {
		    	 toastr.error("error!");
		     }
		})
 	})

    // 初始化标签
    $('.form-control-tag').tagsInput({
        'defaultText':'输入标签'
    });


});