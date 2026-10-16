/*
 * Copyright (c) 2004, 2005, 2006 The Sakai Foundation.
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://opensource.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
$(function() {
    $('a.uimodal').on('click', function() {
        var href = $(this).attr('href');
        var title = $(this).attr('title');
        var loading = $(this).attr('loading');
        $('<div id=QuestionPreview>').html(loading).dialog({
            modal: true,
            open: function () {
                $.get(href).done(function(data, status, XHR){
                    handleData(data);
                });
            },

            close: function (e) {
                $(this).empty();
                $(this).dialog('destroy');
            },
            height: 350,
            width: 540,
            title: title

        });
        return false;
    });
});


function handleData( responseData ) {
    $("#QuestionPreview").html(responseData);
}
