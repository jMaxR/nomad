//
//  StepCallback.hpp
//
//  Created by Christophe Tribes on 2026-01-27.
//  Copyright © 2026 GERAD. All rights reserved.
//

#ifndef __NOMAD_4_5_EVALCALLBACK__
#define __NOMAD_4_5_EVALCALLBACK__

#include "../nomad_nsbegin.hpp"

#include "../Eval/EvalQueuePoint.hpp"

/// Abstract base class for eval callback
class EvalCallback
{
public:
    EvalCallback() = default;
    virtual ~EvalCallback() = default;

    EvalCallback(const EvalCallback&) = delete;
    EvalCallback& operator=(const EvalCallback&) = delete;

    virtual void call(NOMAD::EvalQueuePointPtr & evalQueuePoint, bool& stop) const = 0;
};

class DefaultEvalCallback : public EvalCallback
{
public:
    
    DefaultEvalCallback() = default;
    
    /// Implementation of the abstract call function.
    void call(NOMAD::EvalQueuePointPtr & evalQueuePoint, bool& stop) const override
    {
        stop = false;
    }
};

#include "../nomad_nsend.hpp"

#endif // __NOMAD_4_5_EVALCALLBACK__


