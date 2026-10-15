import io, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
lines = open('sql/50_quality_gap_impl.sql', encoding='utf-8').read().splitlines()
print('total lines:', len(lines))
non_comment = [l for l in lines if l.strip() and not l.strip().startswith('--')]
print('non-comment lines:', len(non_comment))
print('first 3:')
for l in non_comment[:3]:
    print(repr(l[:100]))
slashes = sum(1 for l in non_comment if l.strip() == '/')
print('slash lines:', slashes)
