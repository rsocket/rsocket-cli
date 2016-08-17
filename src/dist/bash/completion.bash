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
        --input|-i)
            _filedir
            return
            ;;
  esac

  if [[ $cur == -* ]]; then
      # TODO parse help automatically
      #_reactivesocket_options=${_reactivesocket_options:=$(_parse_help reactivesocket-cli --help)}
      _reactivesocket_options="--sub --rr --fnf --channel --metadata -i --input --debug --listen"
      COMPREPLY=( $( compgen -W "$_reactivesocket_options" -- "$cur" ) )
      return;
  fi

  # TODO remember recent hosts
  _reactivesocket_hosts="ws://localhost:9898 tcp://localhost:9898"
  COMPREPLY=( $( compgen -W "$_reactivesocket_hosts" -- "$cur" ) )

  __ltrim_colon_completions "$cur"
}

complete -F _reactivesocket_complete reactivesocket-cli
