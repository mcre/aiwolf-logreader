package netmchs_u.mc.aiwolf.logreader;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.aiwolf.client.lib.Content;
import org.aiwolf.client.lib.Topic;
import org.aiwolf.common.data.Agent;
import org.aiwolf.common.data.Role;
import org.aiwolf.common.util.Pair;

public class LogReader {
	private Map<Integer, Role> roleMap = null;
	private Set<Integer> coAgents = null;
	private List<Pair<Role, Role>> coOrder = null; //TODO 最初のCOに変更する
	private Map<String, Map<Pair<Integer, Integer>, Role>> talkRoleMap = null;
	
	private Map<String, Map<Integer, Map<List<?>, Integer>>> counter = null;

	public LogReader() {
		counter = new HashMap<String, Map<Integer, Map<List<?>, Integer>>>();
	}

	public void newGame() {
		roleMap = new HashMap<Integer, Role>();
		coAgents = new HashSet<Integer>();
		coOrder = new ArrayList<Pair<Role, Role>>();
		talkRoleMap = new HashMap<String, Map<Pair<Integer, Integer>, Role>>();
		talkRoleMap.put("talk", new HashMap<Pair<Integer, Integer>, Role>());
		talkRoleMap.put("whisper", new HashMap<Pair<Integer, Integer>, Role>());
	}
	
	public void end() {		
		Map<Pair<Role, Role>, Integer> coCount = new HashMap<Pair<Role, Role>, Integer>();
		for(Pair<Role, Role> p: coOrder)
			countUp(coCount, p);
		count("coConut", coCount);
		count("coOrder", coOrder);
		
		count("gameNum", new ArrayList<Object>());
	}
	
	private void count(String key, Object o1, Object o2) {
		List<Object> l = new ArrayList<Object>(2);
		l.add(o1);
		l.add(o2);
		count(key, l);
	}
	
	private void count(String key, Object o) {
		List<Object> l = new ArrayList<Object>(1);
		l.add(o);
		count(key, l);
	}
	
	private void count(String key, List<?> objs) {	
		int s = roleMap.size();
		if(!counter.containsKey(key))
			counter.put(key, new HashMap<Integer, Map<List<?>, Integer>>());
		if(!counter.get(key).containsKey(s))
			counter.get(key).put(s, new HashMap<List<?>, Integer>());
		
		countUp(counter.get(key).get(s), objs);
	}
	
	private static <T> void countUp(Map<T, Integer> counter, T key) {		
		int now = 0;
		if(counter.containsKey(key))
			now = counter.get(key);
		counter.put(key, now + 1);
	}
	
	private String idToRoleString(Agent agent) {
		if(agent == null)
			return null;
		if(!roleMap.containsKey(agent.getAgentIdx()))
			return null;
		return roleMap.get(agent.getAgentIdx()).toString();
	}
	
	private String idToRoleString(int agentIdx) {
		if(!roleMap.containsKey(agentIdx))
			return null;
		return roleMap.get(agentIdx).toString();
	}
	
	private static String toString(Object obj) {
		if(obj == null)
			return null;	
		return obj.toString();
	}

	public void readLine(String line) {
		String[] cs = line.split(",");
		int day = Integer.parseInt(cs[0]);
		String kind = cs[1];

		if(day == 0 && kind.equals("status")) {
			roleMap.put(Integer.parseInt(cs[2]), Role.valueOf(cs[3]));
			return;
		}
		
		count("logKind", kind);

		if(kind.equals("vote"))
			count("vote", idToRoleString(Integer.parseInt(cs[2])), idToRoleString(Integer.parseInt(cs[3])));
		
		if(kind.equals("talk") || kind.equals("whisper")) {			
			int id = Integer.parseInt(cs[4]);
			Role role = roleMap.get(id);
			Content content = new Content(cs[5]);
			
			talkRoleMap.get(kind).put(new Pair<Integer, Integer>(day, Integer.parseInt(cs[2])), role);
			
			List<String> topic = new ArrayList<String>();
			topic.add(role.toString());
			
			if(content.getTopic().equals(Topic.AGREE) || content.getTopic().equals(Topic.DISAGREE)) {
				Role targetRole = talkRoleMap.get(kind).get(new Pair<Integer, Integer>(content.getTalkDay(), content.getTalkID()));
				topic.add(content.getTopic().toString());
				topic.add(toString(targetRole));
				count(kind + "Topic", topic);
			} else {
				if(content.getTopic().equals(Topic.OPERATOR)) {
					Content reqContent = content.getContentList().get(0);
					topic.add("REQUEST");
					topic.add("sub:" + idToRoleString(reqContent.getSubject()));
					topic.add(reqContent.getTopic().toString());
					topic.add("target:" + idToRoleString(reqContent.getTarget()));
					topic.add("role:" + toString(reqContent.getRole()));
					topic.add("result:" + toString(reqContent.getResult()));
				} else {
					topic.add(content.getTopic().toString());
					topic.add("target:" + idToRoleString(content.getTarget()));
					topic.add("role:" + toString(content.getRole()));
					topic.add("result:" + toString(content.getResult()));
				}
				count(kind + "Topic", topic);
				
				if(content.getTopic().equals(Topic.COMINGOUT)) {
					if(!content.getRole().equals(Role.VILLAGER)) {
						if(!coAgents.contains(id)) { // 2回目以降のCOは登録しない
							coAgents.add(id);
							coOrder.add(new Pair<Role, Role>(role, content.getRole()));
						}
					}
				}
			}
		}
	}

	public Map<Integer, Role> getRoleMap() {
		return roleMap;
	}
	
	public Map<String, Map<Integer, Map<List<?>, Integer>>> getCounter() {
		return counter;
	}

	private static void execFolder(LogReader lr, File folderPath) throws IOException {
		System.out.println(folderPath);
		for(File zip: folderPath.listFiles()) {
			//System.out.println(zip.toString());
			ZipInputStream zis = new ZipInputStream(new FileInputStream(zip));
			BufferedReader br = new BufferedReader(new InputStreamReader(zis));

			String line = null;
			ZipEntry entry = null;
			while((entry = zis.getNextEntry()) != null) {
				entry.getName(); // 消していい
				lr.newGame();
				while((line = br.readLine()) != null)
					lr.readLine(line);
				lr.end();
				zis.closeEntry();
			}
			zis.close();
		}
	}

	public static void main(String[] args) throws IOException {
		LogReader lr = new LogReader();
		execFolder(lr, new File("log/0007_c5737cf"));
		execFolder(lr, new File("log/0199_970663f"));
		execFolder(lr, new File("log/0204_21605e2"));
		
		System.out.println();
		
		Map<String, Map<Integer, Map<List<?>, Integer>>> rcc = lr.getCounter();
		for(String key: rcc.keySet()) {
			if(key.equals("coOrder"))
				continue;
			for(int playerNum: rcc.get(key).keySet())
				for(List<?> os: rcc.get(key).get(playerNum).keySet()) {
					System.out.print(key + "|" + playerNum);
					System.out.print("|" + rcc.get(key).get(playerNum).get(os));
					for(Object o: os)
						System.out.print("|" + toString(o));
					System.out.println();
				}
		}
	}
}
