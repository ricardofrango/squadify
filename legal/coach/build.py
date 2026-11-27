"""Builds the coach app's public pages (privacy policy, account deletion) into `dist/`.

Cloudflare Pages runs this on each push (build command: `pip install markdown && python legal/coach/build.py`,
output directory: `legal/coach/dist`). The Markdown files here are the source; the HTML is never committed.
"""

import html
import pathlib
import shutil

import markdown

HERE = pathlib.Path(__file__).parent
DIST = HERE / "dist"

PAGE = """<!doctype html>
<html lang="{lang}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{title}</title>
<style>
  body {{ font-family: system-ui, sans-serif; line-height: 1.55; color: #1f2a24; background: #fff;
          max-width: 760px; margin: 0 auto; padding: 24px 16px 64px; }}
  h1 {{ font-size: 1.6rem; }} h2 {{ margin-top: 2rem; font-size: 1.2rem; }}
  table {{ border-collapse: collapse; width: 100%; font-size: .9rem; display: block; overflow-x: auto; }}
  th, td {{ border: 1px solid #d6dbd8; padding: 6px 8px; text-align: left; vertical-align: top; }}
  a {{ color: #0b7a4b; }}
  nav {{ margin-bottom: 1.5rem; font-size: .9rem; }}
</style>
</head>
<body>
<nav><a href="/privacy">English</a> · <a href="/pt/privacy">Português</a></nav>
{body}
</body>
</html>
"""

EMAIL = "support@squadcallapp.com"

DELETE_EN = f"""
<h1>Delete your Squadcall Coach account</h1>
<p>You can delete your account and everything in it without the app.</p>
<h2>From the app</h2>
<ol>
  <li>Open Squadcall Coach and sign in.</li>
  <li>Go to <strong>Account</strong> and choose <strong>Delete account</strong>.</li>
  <li>Enter your password and confirm.</li>
</ol>
<p>Your account is deleted at once and can't be recovered. If you own a space, it is deleted with everything in it.</p>
<h2>By email</h2>
<p>If you can't open the app, write to <a href="mailto:{EMAIL}">{EMAIL}</a> from the email address of your account,
with the subject <em>Delete my account</em>. We will reply to confirm.</p>
<p>What is deleted: your account, your profile and photo, the spaces you own with their age groups, players, games,
training and opponents. What stays: nothing of yours. See the <a href="/privacy">privacy policy</a>.</p>
"""

DELETE_PT = f"""
<h1>Eliminar a tua conta da Squadcall Coach</h1>
<p>Podes eliminar a tua conta e tudo o que tem sem usar a app.</p>
<h2>Na app</h2>
<ol>
  <li>Abre a Squadcall Coach e entra na tua conta.</li>
  <li>Vai a <strong>Conta</strong> e escolhe <strong>Eliminar conta</strong>.</li>
  <li>Escreve a tua palavra-passe e confirma.</li>
</ol>
<p>A conta é eliminada logo e não pode ser recuperada. Se tens um espaço, ele é eliminado com tudo o que tem.</p>
<h2>Por email</h2>
<p>Se não conseguires abrir a app, escreve para <a href="mailto:{EMAIL}">{EMAIL}</a> a partir do email da tua conta,
com o assunto <em>Eliminar a minha conta</em>. Respondemos a confirmar.</p>
<p>O que é eliminado: a tua conta, o teu perfil e foto, os espaços que tens com os escalões, jogadores, jogos,
treinos e adversários. O que fica: nada teu. Vê a <a href="/pt/privacy">política de privacidade</a>.</p>
"""


def write(path: pathlib.Path, title: str, lang: str, body: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(PAGE.format(lang=lang, title=html.escape(title), body=body), encoding="utf-8")


def main() -> None:
    if DIST.exists():
        shutil.rmtree(DIST)
    DIST.mkdir(parents=True)

    for lang, source, out, title, delete in [
        ("en", "privacy-policy.en.md", "privacy", "Squadcall Coach — Privacy Policy", DELETE_EN),
        ("pt", "privacy-policy.pt.md", "pt/privacy", "Squadcall Coach — Política de Privacidade", DELETE_PT),
    ]:
        text = (HERE / source).read_text(encoding="utf-8")
        body = markdown.markdown(text, extensions=["tables"])
        write(DIST / out / "index.html", title, lang, body)
        write(DIST / ("delete-account" if lang == "en" else "pt/delete-account") / "index.html",
              "Squadcall Coach — Delete account" if lang == "en" else "Squadcall Coach — Eliminar conta",
              lang, delete)

    # The root goes to the English policy.
    (DIST / "index.html").write_text('<!doctype html><meta charset="utf-8"><meta http-equiv="refresh" content="0; url=/privacy">'
                                     '<a href="/privacy">Privacy policy</a>', encoding="utf-8")


if __name__ == "__main__":
    main()
