cls

REM updates only the parent
call mvnw versions:update-parent     -DallowMajorUpdates=false -DallowMinorUpdates=false -DallowIncrementalUpdates=true

REM updates the other artifacts (with defined property)
REM call mvnw versions:update-properties -DallowMajorUpdates=false -DallowMinorUpdates=true  -DallowIncrementalUpdates=true

pause
cls 

call mvnw versions:display-property-updates versions:display-dependency-updates -DallowMajorUpdates=false -DallowMinorUpdates=true  -DallowIncrementalUpdates=true

pause
cls

call mvnw versions:display-plugin-updates