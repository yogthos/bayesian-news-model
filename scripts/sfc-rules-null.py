"""Reported-only check from the review of bead bnm-4hv.14 (docs/COUPLED-SYSTEMS.md
9g, Review): the holdout's registered null-spread interval under 9f's error model
and under one fitted to the test split's errors (dependence across sectors,
horizons and origins, model-baseline correlation, sector scales). False-pass
rate = P(skill > registered q95) when the true skill is 0.

    uv run --no-project --with numpy python -I scripts/sfc-rules-null.py data/derived/sfc/rules-test.edn
"""
import re, sys, collections
import numpy as np

def rows(path):
    s=open(path).read()
    out=[]
    for m in re.finditer(r'\{:iso "(\w+)",\s*:o (\d+),\s*:h (\d+),\s*:outcome \[([^\]]*)\],\s*:data ([^,\s]+),\s*:split :(\w+),\s*:f\s*\{([^}]*)\}',s):
        iso,o,h,out_,d,sp,f=m.groups()
        fm=dict((k,float(v)) for k,v in re.findall(r':([\w-]+) ([-0-9.eE]+|nil)',f) if v!='nil')
        out.append(dict(iso=iso,o=int(o),h=int(h),outcome=tuple(out_.replace(':','').replace(',',' ').split()),data=float(d),split=sp,f=fm))
    return out

R=rows(sys.argv[1])
T=[r for r in R if r['split']=='test']; H=[r for r in R if r['split']=='holdout']
secs=['hh','nfc','fin','gov']
def err(r,k): return r['f'][k]-r['data']
rng=np.random.default_rng(20261029)
# registered q95 from the report: lo = p - q95
q95={('persistence','h1','nl'):0.189+0.189,('persistence','h35','nl'):0.202+0.102,('persistence','h1','debt'):-0.630+1.229,('persistence','h35','debt'):-0.384+0.891}
def fit(base, kind='nl'):
    P={}
    T_=[r for r in T if r['outcome'][0]==kind]
    by=collections.defaultdict(dict)
    for r in T:
        if r['outcome'][0]=='nl': by[(r['iso'],r['o'],r['h'])][r['outcome'][1]]=err(r,base)
    M=np.array([[v[s] for s in secs] for v in by.values() if all(s in v for s in secs)])
    P['C']=np.corrcoef(M.T); sd=M.std(0); P['scale']=sd/np.sqrt(np.mean(sd**2))
    a=np.array([(err(r,'estimated'),err(r,base)) for r in T_]); P['kappa']=np.corrcoef(a.T)[0,1]
    b=collections.defaultdict(dict)
    for r in T_: b[(r['iso'],r['o'],r['outcome'])][r['h']]=err(r,base)
    p=np.array([(v[3],v[5]) for v in b.values() if 3 in v and 5 in v]); P['c35']=np.corrcoef(p.T)[0,1]
    c=collections.defaultdict(dict)
    for r in T_: c[(r['iso'],r['h'],r['outcome'])][r['o']]=err(r,base)
    P['rho']={}
    for h in [1,3,5]:
        q=np.array([(v[o],v[o+1]) for k,v in c.items() if k[1]==h for o in v if o+1 in v]); P['rho'][h]=np.corrcoef(q.T)[0,1]
    return P
def sim_skill(rows_, PP, structured):
    # rows_: list of (iso,o,h,outcome)
    sse_m=0.0; sse_x=0.0
    for iso in sorted(set(r[0] for r in rows_)):
        rs=[r for r in rows_ if r[0]==iso]
        sc=np.exp(0.5*rng.standard_normal())
        for kind in ['nl','debt']:
            rk=[r for r in rs if r[3][0]==kind]
            if not rk: continue
            idx={r:i for i,r in enumerate(rk)}
            P=PP[kind]; n=len(rk); S=np.zeros((n,n))
            for i,a in enumerate(rk):
                for j,b in enumerate(rk):
                    if structured:
                        do=abs(a[1]-b[1])
                        if a[2]==b[2]: t=P['rho'][a[2]]**do
                        elif {a[2],b[2]}=={3,5}: t=P['c35']*np.sqrt(P['rho'][3]*P['rho'][5])**do
                        else: t=0.0
                        cs=1.0 if kind=='debt' else P['C'][secs.index(a[3][1]),secs.index(b[3][1])]
                        ss=1.0 if kind=='debt' else P['scale'][secs.index(a[3][1])]*P['scale'][secs.index(b[3][1])]
                        S[i,j]=t*cs*ss
                    else:
                        S[i,j]=(0.8**abs(a[1]-b[1])) if (a[2]==b[2] and a[3]==b[3]) else 0.0
            w,V=np.linalg.eigh(S); w=np.clip(w,0,None); L=V*np.sqrt(w)
            k=P['kappa'] if structured else 0.7
            zx=L@rng.standard_normal(n); zi=L@rng.standard_normal(n)
            ex=sc*zx; em=sc*(k*zx+np.sqrt(1-k*k)*zi)
            sse_m_k=(em**2).sum(); sse_x_k=(ex**2).sum()
            yield kind, sse_m_k, sse_x_k
for base in ['persistence','damped']:
  for kind in ['nl','debt']:
    P=fit(base,kind)
    print(base,kind,'fitted: kappa %.2f c35 %.2f rho %s scale %s'%(P['kappa'],P['c35'],{h:round(v,2) for h,v in P['rho'].items()},np.round(P['scale'],2)))
q95.update({('damped','h1','nl'):0.113+0.264,('damped','h35','nl'):0.009+0.295,('damped','h1','debt'):-0.385+0.984,('damped','h35','debt'):-0.276+0.782})
pts={('persistence','h1','nl'):0.189,('persistence','h35','nl'):0.202,('persistence','h1','debt'):-0.630,('persistence','h35','debt'):-0.384,
     ('damped','h1','nl'):0.113,('damped','h35','nl'):0.009,('damped','h1','debt'):-0.385,('damped','h35','debt'):-0.276}
for base in ['persistence','damped']:
    P={k:fit(base,k) for k in ['nl','debt']}
    for hg,hs in [('h1',{1}),('h35',{3,5})]:
        rr=[(r['iso'],r['o'],r['h'],r['outcome']) for r in H if r['h'] in hs]
        for structured in [False,True]:
            res=collections.defaultdict(list)
            for _ in range(2000):
                acc=collections.defaultdict(lambda:[0.0,0.0])
                for kind,m,x in sim_skill(rr,P,structured):
                    acc[kind][0]+=m; acc[kind][1]+=x
                for kind,(m,x) in acc.items(): res[kind].append(1-m/x)
            for kind in ['nl','debt']:
                v=np.array(res[kind]); q=q95[(base,hg,kind)]
                pt=pts[(base,hg,kind)]; a,b=np.quantile(v,0.05),np.quantile(v,0.95)
                print(f'{base:11s} {hg:3s} {kind:4s} {"fitted" if structured else "9f    "} null q05 {a:+.3f} q95 {b:+.3f}; P(skill > registered q95 {q:.3f}) = {np.mean(v>q):.3f}; observed {pt:+.3f} -> [{pt-b:+.3f}, {pt-a:+.3f}]')
