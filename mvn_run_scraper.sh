mvn test-compile exec:java \
  -DargLine="-Xms1G -Xmx4G -XX:PermSize=128m -XX:MaxPermSize=256m" \
  -Dexec.classpathScope=test \
  -Dexec.mainClass=it.sod.open_politici_topics.ScrapeTopics \
  -Dexec.args='http://politici.openpolis.it//argument/tagsVisualization/period/week'

