#!/bin/bash

function printHelp() {
echo "Usage $(basename $0) [ -t <repository>:<tagname> | <Installer Sorgente> | <Personalizzazioni> | <Avanzate> | -h ]"
echo 
echo "Options
-t <TAG>       : Imposta il nome del TAG ed il repository locale utilizzati per l'immagine prodotta 
                 NOTA: deve essere rispettata la sintassi <repository>:<tagname>
-h             : Mostra questa pagina di aiuto

Sorgente:
-v <VERSIONE>  : Imposta la versione della release binaria da utilizzare per il build (default: ${LATEST_GOVPAY_ACA_RELEASE})
-l <FILE>      : Usa un'installer binario sul filesystem locale
-s <SHA256>    : sha256 del jar della release (default: letto dalle API delle release di GitHub)
-q <SHA256>    : sha256 di sql.zip della release (default: letto dalle API delle release di GitHub)
Personalizzazioni:
-e <PATH>      : Imposta il path interno utilizzato per i file di configurazione di govpay 
-f <PATH>      : Imposta il path interno utilizzato per i log di govpay

"
}

DOCKERBIN="$(which docker)"
if [ -z "${DOCKERBIN}" ]
then
   echo "Impossibile trovare il comando \"docker\""
   exit 2 
fi



TAG=
VER=
DB=
LOCALFILE=
SHA256=
SQL_SHA256=
TEMPLATE=
ARCHIVI=
CUSTOM_MANAGER=

REGISTRY_PREFIX=linkitaly
#REGISTRY_PREFIX=localhost

LATEST_LINK="$(curl -qw '%{redirect_url}\n' https://github.com/link-it/govpay-aca-batch/releases/latest 2> /dev/null)"
LATEST_GOVPAY_ACA_RELEASE="${LATEST_LINK##*/}"

while getopts "ht:v:l:e:f:s:q:" opt; do
  case $opt in
    t) TAG="$OPTARG"; NO_COLON=${TAG//:/}
      [ ${#TAG} -eq ${#NO_COLON} -o "${TAG:0:1}" == ':' -o "${TAG:(-1):1}" == ':' ] && { echo "Il tag fornito \"$TAG\" non utilizza la sintassi <repository>:<tagname>"; exit 2; } ;;
    v) VER="$OPTARG"  ;;
    l) LOCALFILE="$OPTARG"
        [ ! -f "${LOCALFILE}" ] && { echo "Il file indicato non esiste o non e' raggiungibile [${LOCALFILE}]."; exit 3; } 
       ;;
    s) SHA256="${OPTARG}" ;;
    q) SQL_SHA256="${OPTARG}" ;;
    e) CUSTOM_GOVPAY_HOME="${OPTARG}" ;;
    f) CUSTOM_GOVPAY_LOG="${OPTARG}" ;;
    h) printHelp
       exit 0
       ;;
    \?)
      echo "Opzione non valida: -$opt"
      exit 1
      ;;
  esac
done


rm -rf buildcontext
mkdir -p buildcontext/
cp -fr commons buildcontext/

DOCKERBUILD_OPT=()
DOCKERBUILD_OPTS=(${DOCKERBUILD_OPTS[@]} '--build-arg' "govpay_aca_fullversion=${VER:-${LATEST_GOVPAY_ACA_RELEASE}}")
[ -n "${TEMPLATE}" ] &&  cp -f "${TEMPLATE}" buildcontext/commons/
[ -n "${CUSTOM_GOVPAY_HOME}" ] && DOCKERBUILD_OPTS=(${DOCKERBUILD_OPTS[@]} '--build-arg' "govpay_aca_home=${CUSTOM_GOVPAY_HOME}")
[ -n "${CUSTOM_GOVPAY_LOG}" ] && DOCKERBUILD_OPTS=(${DOCKERBUILD_OPTS[@]} '--build-arg' "govpay_aca_log=${CUSTOM_GOVPAY_LOG}")
if [ -n "${CUSTOM_RUNTIME}" ]
then
  cp -r ${CUSTOM_RUNTIME}/ buildcontext/runtime
  DOCKERBUILD_OPTS=(${DOCKERBUILD_OPTS[@]} '--build-arg' "runtime_custom_archives=runtime")
fi

# Build immagine installer
if [ -n "${LOCALFILE}" ]
then
  DOCKERFILE="govpay-aca/Dockerfile.daFile"
  cp -f "${LOCALFILE}" buildcontext/
  # Gli script SQL dell'immagine arrivano da target/sql.zip, prodotto dal profilo
  # dist: contiene lo schema dei metadati Spring Batch estratto da
  # spring-batch-core piu' gli script delle tabelle applicative. E' lo stesso
  # archivio che la pipeline pubblica come asset di release.
  if [ -f ../target/sql.zip ]; then
    unzip -q -o ../target/sql.zip -d buildcontext/
  else
    mkdir -p buildcontext/sql
    echo "WARN: ../target/sql.zip non trovato: eseguire 'mvn -Pjar,dist package' per generarlo."
  fi
else
  DOCKERFILE="govpay-aca/Dockerfile.github"
  # Dockerfile.github verifica il jar scaricato: il checksum atteso e' il
  # digest dell'asset della release, che GitHub calcola al caricamento.
  RELEASE="${VER:-${LATEST_GOVPAY_ACA_RELEASE}}"
  if [ -z "${SHA256}" ]
  then
    SHA256="$(curl -fsS "https://api.github.com/repos/link-it/govpay-aca-batch/releases/tags/${RELEASE}" \
      | python3 -c "import json,sys; a=[x for x in json.load(sys.stdin)['assets'] if x['name']=='govpay-aca-batch-${RELEASE}.jar']; print(a[0]['digest'].split(':',1)[1] if a and a[0].get('digest') else '')" 2>/dev/null)"
  fi
  [ -z "${SHA256}" ] && { echo "Impossibile ricavare lo sha256 del jar della release ${RELEASE}: indicarlo con -s."; exit 4; }
  if [ -z "${SQL_SHA256}" ]
  then
    SQL_SHA256="$(curl -fsS "https://api.github.com/repos/link-it/govpay-aca-batch/releases/tags/${RELEASE}" \
      | python3 -c "import json,sys; a=[x for x in json.load(sys.stdin)['assets'] if x['name']=='sql.zip']; print(a[0]['digest'].split(':',1)[1] if a and a[0].get('digest') else '')" 2>/dev/null)"
  fi
  [ -z "${SQL_SHA256}" ] && { echo "Impossibile ricavare lo sha256 di sql.zip della release ${RELEASE}: indicarlo con -q."; exit 4; }
  DOCKERBUILD_OPTS=(${DOCKERBUILD_OPTS[@]} '--build-arg' "govpay_aca_sha256=${SHA256}" '--build-arg' "govpay_aca_sql_sha256=${SQL_SHA256}")
fi


# Build imagine govpay

if [ -z "$TAG" ] 
then
    REPO=${REGISTRY_PREFIX}/govpay-aca-batch
  TAGNAME=${VER:-${LATEST_GOVPAY_ACA_RELEASE}}
  TAG="${REPO}:${TAGNAME}"  
fi



"${DOCKERBIN}" build "${DOCKERBUILD_OPTS[@]}" \
  -t "${TAG}" \
  -f ${DOCKERFILE} buildcontext
RET=$?
[ ${RET} -eq  0 ] || exit ${RET}

exit 0
