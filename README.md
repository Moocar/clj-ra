Ra Game
--------

## Coding

To start coding,

```bash
clj -T:build dev
```

Then, in emacs, run `cider-connect-cljs` and connect to localhost 9001.

Then, run `cider-connect-clj` and connect to the default localhost choice.

## Deploy

```bash
clj -T:build all
```

## Production logs

```bash
ssh anthony@slang-service.bnr.la
journalctl -f -u ra.service
```
