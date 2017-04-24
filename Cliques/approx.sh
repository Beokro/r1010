python ./ApproxCliques/python/sanitize.py . $1.txt
./ApproxCliques/tests/test_cliques_turan_shadow -i $1.edges -m 10 -M 10 -r 3 -n $2
