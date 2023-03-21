import jwt
import hashlib
from datetime import datetime, timedelta, timezone
import asyncio

roll_minutes: int = 2
token: str = None
keys = []


def gen_jwt(token: str, key: str, delta: timedelta, keytype="ES256") -> str:
    """ generate generic server or client jwt """
    sha256tok = hashlib.sha256()
    payload = dict()
    assert(type(token) is str)

    sha256tok.update(token.encode())
    now = datetime.now(tz=timezone.utc)

    payload["token"] = sha256tok.hexdigest()
    payload["iat"] = now
    payload["exp"] = now + delta
    t = jwt.encode(payload, key, algorithm=keytype)
    # t = jwt.encode(payload, key, algorithm="ES256")
    return t if type(t) is str else t.decode()


def roll_token() -> str:
    """ update server jwt """
    global token
    global key
    global roll_minutes

    # to lower requirements for entropy we just encode our last token.
    # this is seemingly random because it is signed and the other party has
    # no knowledge of the private key. sha256 is used to lower its size.
    # obviously the next value of the token field may therefore be computed
    # by an attacker before being issued by us. Therefore the token a client
    # uses should be the sha256 sum of the current JWT issued, as the next token
    # field value is. A useful side effect of this is, that we do not need to
    # save two tokens to preserve the old value: we may as well check our
    # current token value for validity.
    token = token if token else str(datetime.now().microsecond)
    token = gen_jwt(token, key, timedelta(minutes=roll_minutes))
    return token


def get_valid_tokens() -> list[str]:
    global token
    if not token:
        return []
    sha256 = hashlib.sha256()
    sha256.update(token.encode())
    return [sha256.hexdigest(),
        jwt.decode(token, options={"verify_signature": False})["token"]]


def get_actions() -> list[str]:
    global actions
    return ["open"] if not actions else actions


def set_actions(la: list[str]):
    global actions
    for i in la:
        assert(type(i) is str)
    actions = la


def check_jwt(t: str):
    global keys
    assert(type(t) is str)
    tklist = get_valid_tokens()
    inv_sigs = 0

    for k in keys:
        try:
            d = jwt.decode(t, k, algorithms=["RS256", "ES256", "EdDSA"])
            if d:
                if d["token"] not in tklist:
                    print("invalid token")
                    break
                elif d["act"] not in get_actions():
                    print("invalid action")
                    break
                else:
                    return d["act"]
        except jwt.exceptions.InvalidSignatureError:
            inv_sigs += 1
        except Exception as e:
            print("exception", type(e), ": ", str(e))
    else:
        print("invalid jwt:", inv_sigs, "keys did not match")
        return None


def set_signkey(k: str):
    global key
    key = k


def add_key(alg: str, k: str):
    global keys
    keys.append(k)


async def renew_token(callback):
    while True:
        token = roll_token()
        callback(token)
        await asyncio.sleep((roll_minutes/2)*60)

