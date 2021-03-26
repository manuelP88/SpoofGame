### Install scala

Install various command-line tools such as the Scala compiler and build tools ([official guide](https://docs.scala-lang.org/getting-started/index.html#install-scala))

### Run SpoofGame

#### Using a Terminal

- <mark>cd</mark> into SpoofGame directory
- Run <mark>sbt compile</mark>
- Run <mark>sbt run</mark>

#### Using IntelliJ

- Download and install [IntelliJ Community Edition](https://www.jetbrains.com/idea/download/)
- Install the Scala plugin by following the [instructions on how to install IntelliJ plugins](https://www.jetbrains.com/help/idea/managing-plugins.html)
- Open the <mark>build.sbt</mark> file then choose Open as a project
- Run <mark>SpoofGame.scala</mark>

### Example (with three players)

\[Game\] All actors are created and connected!<br/>
[P1] I'm INIT, get start!<br/>
[P1] draw coin<br/>
[P2] draw coin<br/>
[P3] draw coin<br/>
[DrawCoinsHandler] Ok! All players have drawn the coins!<br/>
[P1] I'm INIT, stop draw! My guess is 1!<br/>
[P2] I guess 7!<br/>
[P3] I guess 7!<br/>
[P3] I guess 7!<br/>
[P3] I guess 0!<br/>
[P1] I'm INIT, Ok compute result!<br/>
[P1] Here my coins!<br/>
[P2] Here my coins!<br/>
[P3] Here my coins!<br/>
[GuessHandler] Result #players=3 winnerID=1 #totCoins=4<br/>
[P2] I'm INIT, get start!<br/>
[P2] draw coin<br/>
[P3] draw coin<br/>
[DrawCoinsHandler] Ok! All players have drawn the coins!<br/>
[P2] I'm INIT, stop draw! My guess is 1!<br/>
[P3] I guess 4!<br/>
[P2] I'm INIT, Ok compute result!<br/>
[P2] Here my coins!<br/>
[P3] Here my coins!<br/>
[GuessHandler] Result #players=2 winnerID=2 #totCoins=2<br/>
[Game] P3, you have to pay!<br/>
