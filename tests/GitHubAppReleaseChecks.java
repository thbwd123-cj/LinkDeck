package cn.linkdeck.adb;

public final class GitHubAppReleaseChecks {
  static int checks;
  static void check(boolean yes){checks++;if(!yes)throw new AssertionError("GitHub app check "+checks);}
  static void rejects(String repo,String json)throws Exception{
    try{GitHubAppRelease.parse(repo,json);throw new AssertionError("accepted unsafe release");}
    catch(java.io.IOException expected){checks++;}
  }
  public static void main(String[] args)throws Exception{
    check(GitHubAppRelease.repository("https://github.com/owner/project/").equals("owner/project"));
    for(String invalid:new String[]{"owner","https://evil.example/owner/repo","owner/../repo","owner/repo?x=1","owner/repo/extra"}){
      try{GitHubAppRelease.repository(invalid);throw new AssertionError("accepted repository "+invalid);}catch(java.io.IOException expected){checks++;}
    }
    StringBuilder hash=new StringBuilder();for(int i=0;i<64;i++)hash.append('a');
    String asset="{\"name\":\"tv-arm.apk\",\"size\":12345,\"digest\":\"sha256:"+hash+"\",\"browser_download_url\":\"https://github.com/owner/project/releases/download/v1.2/tv-arm.apk\"}";
    String json="{\"tag_name\":\"v1.2\",\"draft\":false,\"prerelease\":false,\"assets\":["+asset+"]}";
    GitHubAppRelease.Release release=GitHubAppRelease.parse("owner/project",json);
    check(release.apks.size()==1&&release.apks.get(0).size==12345);
    check(release.apks.get(0).digest.length()==64&&release.tag.equals("v1.2"));
    rejects("owner/project",json.replace("github.com/owner/project/","evil.example/owner/project/"));
    rejects("owner/project",json.replace("github.com/owner/project/","github.com/other/project/"));
    rejects("owner/project",json.replace("\"draft\":false","\"draft\":true"));
    rejects("owner/project",json.replace("\"tag_name\":\"v1.2\"","\"tag_name\":\"bad/tag\""));
    rejects("owner/project",json.replace("sha256:"+hash,"sha256:bad"));
    check(GitHubAppRelease.parse("owner/project",json.replace("tv-arm.apk","source.zip")).apks.isEmpty());
    System.out.println("GITHUB APP RELEASE PASSED: "+checks+" checks");
  }
}
