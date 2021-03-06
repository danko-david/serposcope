package serposcope.controllers.admin;

import static com.serphacker.serposcope.models.base.Group.Module.GOOGLE;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ninja.Context;
import ninja.Result;
import ninja.Results;
import ninja.Router;
import ninja.i18n.Messages;
import ninja.params.Param;
import ninja.params.PathParam;
import ninja.session.FlashScope;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.serphacker.serposcope.db.base.BaseDB;
import com.serphacker.serposcope.db.google.GoogleDB;
import com.serphacker.serposcope.models.base.Group;
import com.serphacker.serposcope.models.base.Run;
import com.serphacker.serposcope.models.google.GoogleSearch;
import com.serphacker.serposcope.scraper.captcha.solver.AntiCaptchaSolver;
import com.serphacker.serposcope.scraper.captcha.solver.CaptchaSolver;
import com.serphacker.serposcope.scraper.captcha.solver.DeathByCaptchaSolver;
import com.serphacker.serposcope.scraper.captcha.solver.DecaptcherSolver;
import com.serphacker.serposcope.task.TaskManager;

import serposcope.controllers.BaseController;
import serposcope.controllers.HomeController;

@Singleton
public class SyncController extends BaseController {
	
    private static final Logger LOG = LoggerFactory.getLogger(SyncController.class);
    
    @Inject
    TaskManager taskManager;

    @Inject
    GoogleDB googleDB;

    @Inject
    BaseDB baseDB;

    @Inject
    Router router;
    
    @Inject
    Messages msg;
    
    public Result startTask(
        Context context,
        @PathParam("groupId") Integer groupId,
        @Param("module") Integer moduleId,
        @Param("update") Boolean update
    ) {   

        Run run = null;
        if(Boolean.TRUE.equals(update)){
            run = baseDB.run.findLast(GOOGLE, null, null);
        }
        
        Group group = null;
        if(groupId != null)
        	group = baseDB.group.find(groupId);
        
        if(run == null){
            run = new Run(Run.Mode.MANUAL, Group.Module.GOOGLE, LocalDateTime.now(), group, null);
        } else {
            run.setStatus(Run.Status.RUNNING);
            run.setStarted(LocalDateTime.now());
        }
        
        String ret = "START";
        
        if (!taskManager.startGoogleTask(run)) {
            ret = "RUNNING";
        }
        StringBuilder builder = new StringBuilder("{");
        builder.append("\"status\":\""+ret+"\"");
        builder.append("}");

        return Results.json().renderRaw(builder.toString());
    }
    
    public Result startSingle(
            Context context,
            @PathParam("keywordId") Integer keywordId,
            @Param("module") Integer moduleId
        ) {   
            String ret = "ERROR";
            
            if(null != keywordId)
            {
	            GoogleSearch s = googleDB.search.find(keywordId);
	            
	            if(null == s)
	            {
	            	ret = "KEYWORD_DOESNT_EXISTS";
	            }
	            else
	            {
	            	Run run = new Run(Run.Mode.MANUAL, Group.Module.GOOGLE, LocalDateTime.now(), null, new int[] {keywordId});
	                run.setStatus(Run.Status.RUNNING);
	                run.setStarted(LocalDateTime.now());
		            
		            ret = "START";
		            
		            if (!taskManager.startGoogleTask(run)) {
		                ret = "RUNNING";
		            }
	            }
            
            }
            StringBuilder builder = new StringBuilder("{");
            builder.append("\"status\":\""+ret+"\"");
            builder.append("}");

            return Results.json().renderRaw(builder.toString());
        }
    
    public Result startKeywordsMultiple(
            Context context,
            @PathParam("keywordId") String keywords
        ) {   
            String ret = "ERROR";
            
            String[] kws = keywords.split(",");
            
            Set<Integer> skw = new HashSet<>();
            
            for(String kw:kws)
            {
            	try
            	{
            		Integer id = Integer.parseInt(kw);
            		if(null != googleDB.search.find(id))
            		{
            			skw.add(id);
            		}
            	}
            	catch(Exception e)
            	{}
            }
            
            if(skw.isEmpty())
            {
            	ret = "NO_VALID_KEYWORD_SPECIFIED";
            }
            else
            {
            	int[] rids = new int[skw.size()];
            	int ep = 0;
            	for(Integer i:skw)
            	{
            		rids[ep++] = i;
            	}
            	
            	Run run = new Run(Run.Mode.MANUAL, Group.Module.GOOGLE, LocalDateTime.now(), null, rids);
                run.setStatus(Run.Status.RUNNING);
                run.setStarted(LocalDateTime.now());
	            
	            ret = "START";
	            
	            if (!taskManager.startGoogleTask(run)) {
	                ret = "RUNNING";
	            }
            }
            
            StringBuilder builder = new StringBuilder("{");
            builder.append("\"status\":\""+ret+"\"");
            builder.append("}");

            return Results.json().renderRaw(builder.toString());
        }
    
    public Result abortTask(
        Context context,
        @Param("id") Integer runId
    ) {
    	StringBuilder builder = new StringBuilder("{");
    	String ret = "OK";
    	
        if (runId == null) {
            //ret = "error.invalidId";
            List<Run> runs = baseDB.run.findRunning();
            for(Run run : runs)
            {
            	switch (run.getModule()) {
		            case GOOGLE:
		                if (taskManager.abortGoogleTask(true)) {
		                    ret = "admin.task.abortingTask";
		                } else {
		                    ret = "admin.task.failAbort";
		                }
		                break;
		
		            default:
		                ret = "error.invalidModule";
		        }
            }
        }
        else
        {
	        Run run = baseDB.run.find(runId);
	        if (run == null) {
	            ret = "error.invalidRun";
	        }
	        else
	        {
		        if (run.getStatus() != Run.Status.RUNNING) {
		            ret = "error.invalidRun";
		        }
		        else
		        {
			        switch (run.getModule()) {
			            case GOOGLE:
			                if (taskManager.abortGoogleTask(true)) {
			                    ret = "admin.task.abortingTask";
			                } else {
			                    ret = "admin.task.failAbort";
			                }
			                break;
			
			            default:
			                ret = "error.invalidModule";
			
			        }
		        }
	        }
        }
        builder.append("\"status\":\""+ret+"\"");
        builder.append("}");
        return Results.json().renderRaw(builder.toString());
    }
    
    public Result testCaptcha(
        Context context,
        @Param("service") String captchaService,
        @Param("user") String captchaUser,
        @Param("pass") String captchaPass,
        @Param("api") String captchaApiKey
    ){
        
        CaptchaSolver solver = null;
        if(captchaService != null){
            switch(captchaService){
                case "dbc":
                    if(!StringUtils.isEmpty(captchaUser) && !StringUtils.isEmpty(captchaPass)){
                        solver = new DeathByCaptchaSolver(captchaUser, captchaPass);
                    }
                    break;
                case "decaptcher":
                    if(!StringUtils.isEmpty(captchaUser) && !StringUtils.isEmpty(captchaPass)){
                        solver = new DecaptcherSolver(captchaUser, captchaPass);
                    }
                    break;
                case "anticaptcha":
                    if(!StringUtils.isEmpty(captchaApiKey)){
                        solver = new AntiCaptchaSolver(captchaApiKey);
                    }
                    break;
            }
        }
        
        if(solver == null){
            return Results.ok().text().render(msg.get("admin.settings.invalidService", context, Optional.absent()).get());
        }
        
        try {
            if(!solver.init()){
                return Results.ok().text().render(msg.get("admin.settings.failedInitService", context, Optional.absent(), solver.getFriendlyName()).get());
            }

            if(!solver.testLogin()){
                return Results.ok().text().render(msg.get("admin.settings.invalidServiceCredentials", context, Optional.absent(), solver.getFriendlyName()).get());
            }

            float balance = solver.getCredit();
            return Results.ok().text().render("OK, balance = " + balance);
        }finally{
            try{solver.close();}catch(Exception ex){}
        }
    }

}
