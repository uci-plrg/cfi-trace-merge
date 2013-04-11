package analysis.drLaunch;

public class DRLauncherUtils {
	
	/**
	 * This function read the standard script file, which is
	 * supposed to have a nice structure in the file, and split
	 * the script into a few pieces according the server.config
	 * file so that the script can be executed in parallel on different
	 * machines
	 * 
	 * After calling this function, the splitted 
	 * 
	 * @param scriptName
	 */
	public static void splitScript(String scriptName) {
		
	}
	
	public static void main(String[] argvs) {
		System.out.println(Configuration.getConfig().getGeneratedScriptsPath());
	}
}
