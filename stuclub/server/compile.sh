javac -cp .:lib/* -d bin $(find . -name "*.java")
cp sql/database.properties bin/server/sql
cp config/service.properties bin/server/config
