#@IgnoreInspection BashAddShebang
_reactivesocket_complete()
{
  local cur prev words cword
  COMPREPLY=()
	job="${COMP_WORDS[0]}"
	cur="${COMP_WORDS[COMP_CWORD]}"
	prev="${COMP_WORDS[COMP_CWORD-1]}"

	_get_comp_words_by_ref -n : cur

  case $prev in
        # options with an argument we don't currently help with, everything else is assumed to be handled
        # below in case statement or has no arguments so drops through to the url handling near the end
        -i | --input | --keepalive | -m | --metadata | --timeout | --setup)
            return
            ;;
        --metadataFormat | --dataFormat)
            COMPREPLY=( $( compgen -W "text json cbor binary application/json application/cbor text/plain application/x-www-form-urlencoded application/x.reactivesocket.meta+cbor application/binary" -- "$cur" ) )
            return
            ;;
  esac

  if [[ $cur == -* ]]; then
      # TODO parse help automatically
      #_reactivesocket_options=${_reactivesocket_options:=$(_parse_help reactivesocket-cli --help)}
      _reactivesocket_options="--sub --str --rr --fnf --channel --metadataPush --ops -i --input --debug --server --keepalive -h --help -m --metadata --setup --timeout --metadataFormat --dataFormat"
      COMPREPLY=( $( compgen -W "$_reactivesocket_options" -- "$cur" ) )
      return;
  fi

  # TODO remember recent hosts
  _reactivesocket_hosts="tcp://localhost:9898"
  COMPREPLY=( $( compgen -W "$_reactivesocket_hosts" -- "$cur" ) )

  __ltrim_colon_completions "$cur"
}

complete -F _reactivesocket_complete reactivesocket-cli
