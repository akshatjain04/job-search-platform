#!/usr/bin/env node
import {spawnSync} from 'node:child_process';
import {existsSync,mkdirSync,writeFileSync} from 'node:fs';
import {resolve} from 'node:path';

const args=process.argv.slice(2),root=resolve(args.find(a=>a.startsWith('--root='))?.slice(7)||process.cwd());
const full=args.includes('--full');
const evidence={startedAt:new Date().toISOString(),mode:full?'full':'static',commands:[],semanticAcceptance:'Requires agent review against every current acceptance criterion'};
function run(program,parameters,cwd=root,extraEnvironment={}){const command=process.platform==='win32'&&['npm','mvn'].includes(program)?program+'.cmd':program;const result=spawnSync(command,parameters,{cwd,env:{...process.env,...extraEnvironment},stdio:'inherit',shell:process.platform==='win32'&&command.endsWith('.cmd')});evidence.commands.push({program,args:parameters,exitCode:result.status,error:result.error?.code});if(result.error||result.status!==0)throw new Error(program+' failed; inspect the actual output and repair before continuing.');}
try{
 for(const file of ['docs/MASTER_IMPLEMENTATION_SPEC.md','docs/ACCEPTANCE_CRITERIA.md','docs/IMPLEMENTATION_STATUS.md','docs/reference/ai_job_search_platform_architecture_cost_optimized.docx'])if(!existsSync(resolve(root,file)))throw new Error('Missing source of truth: '+file);
 run('node',['scripts/verify-repository.mjs']);
 run('node',['--test','scripts/configuration.test.mjs']);
 if(full){
  run('npm',['ci','--no-audit','--no-fund']);
  run('mvn',['-f','backend/pom.xml','-Pformat','spotless:check','-B','-ntp']);
  run('node',['scripts/platform.mjs','bootstrap','--demo']);
  run('npm',['run','format:check']);
  run('npm',['exec','--','playwright','install','chromium'],resolve(root,'web'));
  run('npm',['run','e2e'],resolve(root,'web'));
  run('node',['scripts/restart-test.mjs']);
  run('docker',['compose','--env-file','.env.example','-f','docker-compose.yml','-f','docker-compose.prod.yml','config','--quiet'],root,{APP_ENV_FILE:resolve(root,'.env.example')});
 }
 evidence.result='PASS_EXECUTED_CHECKS';
}catch(error){evidence.result='FAIL';evidence.error=error.message;console.error(error.message);process.exitCode=1;}
finally{evidence.completedAt=new Date().toISOString();const output=resolve(root,'.local/audit');mkdirSync(output,{recursive:true});writeFileSync(resolve(output,'latest.json'),JSON.stringify(evidence,null,2)+'\n');console.log('Evidence: '+resolve(output,'latest.json'));}
