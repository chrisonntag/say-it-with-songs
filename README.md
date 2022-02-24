# say-it-with-songs

This small scala project was meant to find an entry to the Scala programming language. 
It is my first project using scala and scrapes SoundCloud in order to find songs that match certain words of a given text.

## Install (on uberspace)
Create a new service file in ```~/etc/services.d/say-it-with-songs.ini``` with the following content
```
[program:sayit]
directory=%(ENV_HOME)s/apps/say-it-with-songs
command=java -jar %(ENV_HOME)s/apps/say-it-with-songs/target/scala-2.13/say-it-with-songs_2.13-0.1.war --httpPort=8081 --enable-future-java
```

### Connect redis 
Redis is used for "caching" the song URL's for certain words. Install redis on your server and let it run on port ```6379```. 
Follow the official [uberspace](https://lab.uberspace.de/guide_redis.html) instructions for more info.

## Code style
This project uses [Scalastyle](https://scalastyle.org/sbt.html) to ensure a consistent codestyle and best-practices. 
Use 
```
sbt scalastyleGenerateConfig
```

to generate a configuration file and 
```
sbt scalastyle
```

to check the project using the sbt shell.