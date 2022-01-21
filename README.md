# say-it-with-songs

This small scala project was meant to find an entry to the Scala programming language. 
It is my first project using scala and scrapes SoundCloud in order to find songs that match certain words of a given text.

## Connect redis
Redis is used for "caching" the song URL's for certain words. Install redis on your server and let it run on port ```6379```.